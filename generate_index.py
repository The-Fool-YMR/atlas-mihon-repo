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
    python3 generate_index.py --code 2        # bump to 1.6.2 (also edit
                                              # EXTENSION["version"] to match)

Rules enforced here (they are Mihon's, not ours):
  - version is "1.6.{code}" (the tachiyomix 1.6 line: the patch
    number IS the version code, so Mihon can compare updates; the
    pb lib version is version minus its last component, i.e. "1.6").
  - apk filename must be tachiyomi-{lang}.{slug}-v{version}.apk.
  - the source id is a string in the index, and it MUST equal the id
    Mihon derives at runtime for the installed source: MD5 of
    "name.lowercase()/lang/versionId", first 8 bytes big-endian,
    sign bit cleared (see the library's HttpSource docs and
    Keiyoushi's ExtensionPlugin.kt computeSourceId — verified
    2026-10-29 against 500 real Keiyoushi index entries: 480 match
    with versionId 1 once the name is lowercased). For ATLAS that
    is MD5("atlas/en/1") = 6566433822980473896. Changing it
    orphans every Mihon library that already uses ATLAS, and a
    wrong id means Mihon never matches the installed source to
    this entry (no updates, no store actions). NEVER use the old
    FNV-1a package-hash id (2685846169743944013) — that was the
    pre-1.6 scheme and cannot be produced by the extension anymore.
  - nsfw is 0 (ATLAS is not an 18+ source). The pb does NOT carry
    nsfw; it carries ContentWarning (field 7): nsfw 0 -> SAFE (1),
    nsfw 1 -> NSFW (3). Writing 3 for a safe source puts the 18+
    badge on it in Mihon (seen live 2026-10-29).
  - baseUrl in the JSON index must match the extension code's
    baseUrl (https://api.atlascomics.fun — the API, not the
    website). The pb is different: its source field 4 is homeUrl,
    the WEBSITE link Mihon opens from the extension row's globe
    icon — that one is the site, https://atlascomics.fun. The
    extension's real API base lives only in AtlasComics.kt, so
    pointing homeUrl at the site cannot break chapter fetching.
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
    "apk": "tachiyomi-en.atlascomics-v1.6.1.apk",
    "lang": "en",
    "code": 1,
    "version": "1.6.1",
    "nsfw": 0,
    "sources": [
        {
            "name": "ATLAS",
            "lang": "en",
            # Mihon-derived id: MD5("atlas/en/1"), first 8 bytes
            # big-endian, sign bit cleared. Verified 2026-10-29.
            # NEVER change this.
            "id": "6566433822980473896",
            "baseUrl": "https://api.atlascomics.fun",
        }
    ],
}

EXTENSIONS = [EXTENSION]

# --- index.pb (Mihon 0.20+ tachiyomix format) -------------------------
# Wire layout, CONFIRMED against Mihon's own source
# (mihonapp/mihon, data/.../extension/model/NetworkExtensionStore.kt,
# read 2026-10-29 — field names below are Mihon's, not guesses).
# Field numbers, not names, are what Mihon reads:
#   top:  1 = repo title (name), 2 = repo short name (badgeLabel),
#         3 = signingKey (64-hex; see note below),
#         4 = contact message {1 = website, 2 = discord (optional)},
#         101 = entries wrapper, repeated 1 = one extension each
#   ext:  1 = name, 2 = pkg,
#         3 = resources message {1 = ABSOLUTE apk url,
#             2 = ABSOLUTE icon url},
#         4 = lib version ("1.6" — version minus its last component),
#         5 = code (varint), 6 = full version string,
#         7 = contentWarning enum: 0 UNSPECIFIED, 1 SAFE, 2 MIXED,
#             3 NSFW (Mihon shows the 18+ badge for NSFW only;
#             UNSPECIFIED also maps to SAFE in the UI, but SAFE is
#             the correct value for a safe source),
#         8 = repeated source messages {1 = id (int64 varint),
#             2 = name, 3 = lang, 4 = homeUrl, 5 = mirrorUrls,
#             7 = message} — homeUrl is the WEBSITE link, not the
#             extension's API base (see module docstring).
#   NOTE on field 3 (signingKey): it is the APK signing-key
#   certificate's SHA-256 fingerprint (lowercase hex). It is NOT
#   the sha256 of index.json, index.min.json, the icon, the stored
#   gzip file, or the protobuf without field 3 (all tested against
#   Comix before the schema was confirmed). Mihon checks it at
#   install time against the APK's actual signing certificate, so
#   it MUST equal that certificate's fingerprint — otherwise
#   installs land Untrusted until manually trusted. The value
#   below is the CI debug keystore's fingerprint (the key every
#   CI build signs with). Before publishing, it MUST be confirmed
#   against the built APK's certificate (keytool -printcert
#   -jarfile <apk>); if the APK's cert fingerprint differs, fix
#   this constant and regenerate — never publish a mismatched key.
SIGNING_KEY = "4f4c6409acc2396a3c4d58946719697c8380d8aaa2b1b1854a2954b2e2be7c77"
REPO_TITLE = "ATLAS Mihon Extensions"
REPO_SHORT = "ATLAS"
REPO_URL = "https://github.com/The-Fool-YMR/atlas-mihon-repo"
RAW_BASE = "https://raw.githubusercontent.com/The-Fool-YMR/atlas-mihon-repo/repo"
# The website Mihon opens from the extension row's globe icon
# (pb source field 4, homeUrl). Display-only; the extension's API
# base stays https://api.atlascomics.fun in AtlasComics.kt and in
# the JSON index's baseUrl.
ATLAS_SITE_URL = "https://atlascomics.fun"
# JSON nsfw (0/1) -> pb ContentWarning enum value.
NSFW_TO_CONTENT_WARNING = {0: 1, 1: 3}


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
    # Field 4 is homeUrl (website link), NOT the JSON baseUrl (API).
    out += _string_field(4, ATLAS_SITE_URL)
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
    out += _varint_field(7, NSFW_TO_CONTENT_WARNING[ext["nsfw"]])
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

    pb = _encode_top(entries, SIGNING_KEY)
    (ROOT / "index.pb").write_bytes(gzip.compress(pb, mtime=0))
    print(f"Wrote index.pb ({len(pb)} bytes protobuf, "
          f"signingKey {SIGNING_KEY[:12]}…)")


def validate(entries):
    for ext in entries:
        for field in ("name", "pkg", "apk", "lang", "code", "version", "nsfw", "sources"):
            assert field in ext, f"missing field {field}"
        assert ext["version"] == f"1.6.{ext['code']}", (
            f"version {ext['version']} does not match code {ext['code']} "
            "(tachiyomix 1.6 line: version is 1.6.<code>)"
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
        entries[0]["version"] = f"1.6.{args.code}"
        entries[0]["apk"] = re.sub(
            r"-v1\.6\.\d+\.apk$",
            f"-v1.6.{args.code}.apk",
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
