#!/usr/bin/env python3
"""Regenerate index.json / index.min.json for the ATLAS Mihon repo.

Keiyoushi-style repo layout (this whole folder is the published root):

    index.json            pretty-printed index (same content as .min)
    index.min.json        minified index — the file users paste into Mihon
                            on older Mihon / forks (see README for index.pb)
    apk/                  one .apk per extension, named
                          tachiyomi-{lang}.{name}-v{version}.apk
    icon/                 one .png per extension, named after its package:
                          eu.kanade.tachiyomi.extension.en.atlascomics.png

Usage:
    python3 generate_index.py                # uses EXTENSION below
    python3 generate_index.py --code 2        # bump to 1.4.2 (also edit
                                              # EXTENSION["version"] to match)

Rules enforced here (they are Mihon's, not ours):
  - version must be "1.4.{code}" (the Keiyoushi convention: the patch
    number IS the version code, so Mihon can compare updates).
  - apk filename must be tachiyomi-{lang}.{slug}-v{version}.apk.
  - the source id is a string in the index, and it MUST equal the
    pinned `override val id` in AtlasComics.kt — changing it orphans
    every Mihon library that already uses ATLAS.
  - nsfw is 0 (ATLAS is not an 18+ source).
  - baseUrl in the index must match the extension code's baseUrl
    (https://api.atlascomics.fun — the API, not the website).
"""

import argparse
import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent

# The one and only extension this repo serves. Add more entries to this
# list if ATLAS ever ships a second extension (e.g. another language).
EXTENSION = {
    "name": "Tachiyomi: ATLAS",
    "pkg": "eu.kanade.tachiyomi.extension.en.atlascomics",
    "apk": "tachiyomi-en.atlascomics-v1.4.1.apk",
    "lang": "en",
    "code": 1,
    "version": "1.4.1",
    "nsfw": 0,
    "sources": [
        {
            "name": "ATLAS",
            "lang": "en",
            # FNV-1a 64-bit hash of the package name; pinned in
            # AtlasComics.kt as `override val id`. NEVER change this.
            "id": "2685846169743944013",
            "baseUrl": "https://api.atlascomics.fun",
        }
    ],
}

EXTENSIONS = [EXTENSION]


def validate(entries):
    for ext in entries:
        for field in ("name", "pkg", "apk", "lang", "code", "version", "nsfw", "sources"):
            assert field in ext, f"missing field {field}"
        assert ext["version"] == f"1.4.{ext['code']}", (
            f"version {ext['version']} does not match code {ext['code']} "
            "(Keiyoushi convention: version is 1.4.<code>)"
        )
        slug = ext["pkg"].rsplit(".", 1)[-1]
        expected_apk = f"tachiyomi-{ext['lang']}.{slug}-v{ext['version']}.apk"
        assert ext["apk"] == expected_apk, (
            f"apk {ext['apk']} != expected {expected_apk}"
        )
        assert isinstance(ext["code"], int) and ext["code"] >= 1
        assert ext["nsfw"] in (0, 1)
        for src in ext["sources"]:
            assert src["lang"] == ext["lang"]
            assert re.fullmatch(r"[0-9]+", src["id"]), "source id must be a numeric string"
            assert src["baseUrl"].startswith("https://")
        icon = ROOT / "icon" / f"{ext['pkg']}.png"
        assert icon.is_file(), f"missing icon {icon}"
        apk = ROOT / "apk" / ext["apk"]
        if not apk.is_file():
            print(f"WARNING: {apk} not present yet — the index is written "
                  "anyway, but Mihon installs will fail until the real "
                  "APK is dropped into apk/ and this script is re-run.",
                  file=sys.stderr)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--code", type=int, help="new version code (also update EXTENSION version)")
    args = parser.parse_args()
    entries = [dict(e) for e in EXTENSIONS]
    if args.code is not None:
        entries[0]["code"] = args.code
        entries[0]["version"] = f"1.4.{args.code}"
        entries[0]["apk"] = re.sub(
            r"-v1\.4\.\d+\.apk$",
            f"-v1.4.{args.code}.apk",
            entries[0]["apk"],
        )
    validate(entries)
    pretty = json.dumps(entries, indent=2, ensure_ascii=False) + "\n"
    minified = json.dumps(entries, separators=(",", ":"), ensure_ascii=False)
    (ROOT / "index.json").write_text(pretty, encoding="utf-8")
    (ROOT / "index.min.json").write_text(minified, encoding="utf-8")
    # Round-trip check: both files must decode to the same data.
    assert json.loads(pretty) == json.loads(minified)
    print(f"Wrote index.json + index.min.json ({len(entries)} extension, "
          f"code {entries[0]['code']}, version {entries[0]['version']})")


if __name__ == "__main__":
    main()
