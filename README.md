# ATLAS Mihon extension store (repo)

**Live store links (add one in Mihon):**
- Mihon 0.20+ ("extension store"): `https://github.com/The-Fool-YMR/atlas-mihon-repo/raw/repo/index.pb`
- Older Mihon / forks (Komikku etc.): `https://github.com/The-Fool-YMR/atlas-mihon-repo/raw/repo/index.min.json`

This folder IS the repo. Publish this whole folder, intact, at one URL
and Mihon users add that URL once — then ATLAS appears in their
Extensions list and updates itself whenever a new version is published
here. It mirrors the Keiyoushi layout:

```
index.json            pretty index (same data as the minified one)
index.min.json        minified index — the file users paste into Mihon
                        on older Mihon / forks
index.pb              gzip-compressed protobuf index — the file Mihon
                        0.20+ fetches (same entry data as the JSON)
apk/                  tachiyomi-en.atlascomics-v1.4.1.apk
icon/                 eu.kanade.tachiyomi.extension.en.atlascomics.png
generate_index.py     regenerates all three indexes (with validation)
```

`index.pb` is binary: the text-only GitHub snapshot push cannot
carry it, so after generating it, upload it to the `repo` branch
via the GitHub web UI (repo → branch `repo` → Add file → Upload
files) or another binary-capable route. The JSON indexes and this
README travel by snapshot push as usual.

Mihon resolves the APK and icon paths **relative to the index URL**,
so the tree must stay together: if the index lives at
`https://example.com/mihon/index.min.json`, the APK must be at
`https://example.com/mihon/apk/tachiyomi-en.atlascomics-v1.4.1.apk`.

## The extension in this repo

- Display name: **Tachiyomi: ATLAS**
- Package: `eu.kanade.tachiyomi.extension.en.atlascomics`
- Source: **ATLAS**, language `en`
- Source id: `2685846169743944013` — FNV-1a 64-bit hash of the package
  name, pinned in the extension code as `override val id`. **Never
  change it**: Mihon matches library entries and backups to this id.
- Version: `1.4.1`, code `1` (Keiyoushi convention: version is
  `1.4.<code>`; the patch number IS the version code)
- NSFW: `0`
- Source baseUrl in the index matches the extension code:
  `https://api.atlascomics.fun`

## For users: adding this store to Mihon

1. In Mihon: **Browse → Extensions → ⋮ → Extension repos** (on newer
   Mihon: **More → Settings → Browse → Extension repos**) → **Add**.
2. Paste the store's index URL (see "Publishing" below for the exact
   URL once a host is chosen) and confirm.
3. Back in **Browse → Extensions**, find **Tachiyomi: ATLAS** and tap
   **Install** (allow "install unknown apps" for Mihon if asked).
4. **Browse → Sources → ATLAS** is now ready to read from.

No login is needed; the extension only uses ATLAS's public API.

## Publishing: pick one host (the tree is identical either way)

### Option A — GitHub (the Keiyoushi way, recommended for reach) — LIVE

Public repo `The-Fool-YMR/atlas-mihon-repo`, branch `repo`,
containing this folder at its root. Users add:

```
https://github.com/The-Fool-YMR/atlas-mihon-repo/raw/repo/index.pb      (Mihon 0.20+)
https://github.com/The-Fool-YMR/atlas-mihon-repo/raw/repo/index.min.json (older Mihon / forks)
```

GitHub serves the APKs over its CDN, and no server of yours is
involved at install time. Remember: `index.pb` and the icon PNG are
binary, so they travel via the GitHub web UI, not the snapshot push.

### Option B — Your own site

Serve this folder under `https://atlascomics.fun/mihon/` (the apex is
already on the VPS, so no new DNS record is needed — just a static
path on the site). Users then add:

```
https://atlascomics.fun/mihon/index.min.json
```

### Option C — The API subdomain

Serve this folder under `https://api.atlascomics.fun/mihon/` via a
static route in the API Caddy block. Users then add:

```
https://api.atlascomics.fun/mihon/index.min.json
```

Whichever host is chosen, the URL users paste is the index URL, and
the APK/icon paths resolve relative to it — nothing else changes.

## Releasing a new version

1. Bump the extension's version code by 1 and rebuild the APK.
2. Drop the new APK into `apk/` as
   `tachiyomi-en.atlascomics-v1.4.<new code>.apk`.
3. Run `python3 generate_index.py --code <new code>` (it rewrites
   all three indexes and validates names, versions, icon, and
   source id).
4. Publish the updated folder — and re-upload `index.pb` via the
   web UI, since it is binary. Mihon will offer the update to
   everyone who installed from this store.

## Two things that are NOT done yet (honest status)

1. **The APK itself.** This machine has no Android SDK/JDK, so the
   real `tachiyomi-en.atlascomics-v1.4.1.apk` could not be built here
   (same blocker as the extension source delivery). `apk/` currently
   holds only a `.gitkeep`. Until the built APK is dropped in, Mihon
   will list ATLAS from this store but the install download will fail.
   Build it via the extension-template route in the extension README,
   then publish.
2. **Uploading `index.pb` to the repo branch.** `index.pb` is now
   generated by `generate_index.py` and its wire layout is validated
   against the published Comix and Keiyoushi indexes (same field
   numbers, same entry data as the JSON). It is NOT on GitHub yet,
   because it is binary and the snapshot push carries text only —
   upload it via the GitHub web UI as described above. Two fields
   carry honest uncertainty: the per-extension constant `7 = 3`
   (observed identical across repos, lib versions and nsfw states,
   meaning unknown but safe to copy) and the top-level repo hash
   (a self-consistent sha256 placeholder until the APK signing
   certificate exists to fingerprint — see `generate_index.py`).
