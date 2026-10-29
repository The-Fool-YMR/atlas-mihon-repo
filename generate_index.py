#!/usr/bin/env python3
"""Regenerate index.json / index.min.json for the ATLAS Mihon repo.

Keiyoushi-style repo layout (this whole folder is the published root):

    index.json            pretty-printed index (same content as .min)
    index.min.json        minified index — the file users paste into Mihon
                            on older Mihon / forks (see README for index.pb)
    index.pb              gzip-compressed protobuf index — the file Mihon
                            0.20+ ("extension store") fetches. Same entry
                            data as the JSON, in tachiyomix wire layout
                            (reverse-engineered from the published Comix
                            and Keiyoushi indexes — see write_index_pb).
                            BINARY: GitHub snapshot pushes cannot carry
                            it; upload it to the repo branch via the
                            GitHub web UI (or another binary-capable
                            route) after running this script.
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

# --- index.pb (Mihon 0.20+ tachiyomix format) -------------------------
# Wire layout, reverse-engineered from the published Comix (single
# entry) and Keiyoushi (multi entry) indexes, both gzip-compressed
# protobuf. Field numbers, not names, are what Mihon reads:
#   top:  1 = repo title, 2 = repo short name,
#         3 = 64-hex repo hash (see note below),
#         4 = links message {1 = website, 2 = discord (optional)},
#         101 = entries wrapper, repeated 1 = one extension each
#   ext:  1 = name, 2 = pkg,
#         3 = urls message {1 = ABSOLUTE apk url, 2 = ABSOLUTE icon url},
#         4 = lib version ("1.4" — version minus its last component),
#         5 = code (varint), 6 = full version string,
#         7 = 3 (constant in every entry observed, across repos, lib
#             versions 1.4/1.6 and nsfw 0/1 — so NOT the nsfw flag;
#             its meaning is unconfirmed, but 3 is the observed value),
#         8 = repeated source messages {1 = id (int64 varint),
#             2 = name, 3 = lang, 4 = baseUrl}
#   NOTE on field 3: its value is repo-specific and is NOT the sha256
#   of index.json, index.min.json, the icon, the stored gzip file, or
#   the protobuf without field 3 (all tested against Comix). The
#   remaining candidates are the APK hash / signing-certificate
#   fingerprint / a repo-generated id — none computable until the
#   APK and its signing key exist. Until then field 3 is the sha256
#   of the protobuf encoded without field 3 (self-consistent
#   placeholder). Mihon cannot validate it at store-add time (no
#   APK is fetched when adding a store); worst case at install
#   time the extension lands Untrusted until the user trusts it,
#   which is the normal flow for third-party repos.
REPO_TITLE = "ATLAS Mihon Extensions"
REPO_SHORT = "ATLAS"
REPO_URL = "https://github.com/The-Fool-YMR/atlas-mihon-repo"
RAW_BASE = "https://raw.githubusercontent.com/The-Fool-YMR/atlas-mihon-repo/repo"


def _varint(n):
    out = bytearray()
    while True:
        b = n & 0x7F
        n >>= 7
        if n:
            out.append(b | 0x80)
        else:
            out.append(b)
            return bytes(out)


def _field(num, wt):
    return _varint((num << 3) | wt)


def _bytes_field(num, payload):
    return _field(num, 2) + _varint(len(payload)) + payload


def _string_field(num, text):
    return _bytes_field(num, text.encode("utf-8"))


def _varint_field(num, value):
    return _field(num, 0) + _varint(value)


def _encode_source(src):
    out = bytearray()
    out += _varint_field(1, int(src["id"]))
    out += _string_field(2, src["name"])
    out += _string_field(3, src["lang"])
    out += _string_field(4, src["baseUrl"])
    return bytes(out)


def _encode_ext(ext):
    out = bytearray()
    out += _string_field(1, ext["name"])
    out += _string_field(2, ext["pkg"])
    urls = bytearray()
    urls += _string_field(1, f"{RAW_BASE}/apk/{ext['apk']}")
    urls += _string_field(2, f"{RAW_BASE}/icon/{ext['pkg']}.png")
    out += _bytes_field(3, bytes(urls))
    lib_version = ext["version"].rsplit(".", 1)[0]
    out += _string_field(4, lib_version)
    out += _varint_field(5, ext["code"])
    out += _string_field(6, ext["version"])
    out += _varint_field(7, 3)
    for src in ext["sources"]:
        out += _bytes_field(8, _encode_source(src))
    return bytes(out)


def _encode_top(entries, repo_hash):
    out = bytearray()
    out += _string_field(1, REPO_TITLE)
    out += _string_field(2, REPO_SHORT)
    if repo_hash is not None:
        out += _string_field(3, repo_hash)
    links = bytearray()
    links += _string_field(1, REPO_URL)
    out += _bytes_field(4, bytes(links))
    wrapper = bytearray()
    for ext in entries:
        wrapper += _bytes_field(1, _encode_ext(ext))
    out += _bytes_field(101, bytes(wrapper))
    return bytes(out)


def write_index_pb(entries):
    import gzip
    import hashlib

    without_hash = _encode_top(entries, None)
    repo_hash = hashlib.sha256(without_hash).hexdigest()
    pb = _encode_top(entries, repo_hash)
    (ROOT / "index.pb").write_bytes(gzip.compress(pb, mtime=0))
    print(f"Wrote index.pb ({len(pb)} bytes protobuf, "
          f"repo hash {repo_hash[:12]}…)")


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
    write_index_pb(entries)
    # Round-trip check: both files must decode to the same data.
    assert json.loads(pretty) == json.loads(minified)
    print(f"Wrote index.json + index.min.json ({len(entries)} extension, "
          f"code {entries[0]['code']}, version {entries[0]['version']})")


if __name__ == "__main__":
    main()
