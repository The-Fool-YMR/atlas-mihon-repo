# ATLAS — Mihon extension source (CI build)

This branch (`main`) of `atlas-mihon-repo` holds the extension
source; the publishable store index lives on the separate `repo`
branch of this same repository.

The ATLAS source for Mihon, talking to the public JSON API at
**api.atlascomics.fun** (not scraping the website).

- Source name in Mihon: **ATLAS**, language English (`en`)
- Package: `eu.kanade.tachiyomi.extension.en.atlascomics`
- Main class: `AtlasComics`
- Version: **1.4.1**, code **1** (the store index lists this version;
  bump code + version together on every release)

## How the APK gets built

No local Android Studio or SDK needed. Pushing to this repo (or
pressing "Run workflow" in the Actions tab) runs
`.github/workflows/build.yml`, which builds the debug APK in the
cloud. Download it from the finished run's artifacts — it is named
`tachiyomi-en.atlascomics-v1.4.1.apk`.

## Signing

The build signs with the committed debug keystore (the standard
public Android debug key, password `android`), so every build has
the same signature and Mihon updates keep working. The keystore is
stored as `debug.keystore.b64` (base64 text) and decoded back to
`debug.keystore` by the workflow before building. Do not mix this
with local builds: a local machine's auto-generated debug key is
different, and Mihon refuses updates signed with a different key.

## Test before publishing

Install the APK on a phone first: Mihon → Browse → Sources →
ATLAS should appear, and browse / latest / search should list
series with covers. Only then put the APK in the store repo's
`apk/` folder — the store index must also be regenerated with this
keystore's fingerprint, or Mihon installs it as Untrusted.

## The store

The publishable Mihon store lives in a separate repo
(`atlas-mihon-repo`): `index.json` / `index.min.json` / `index.pb`,
`apk/`, `icon/`, and `generate_index.py`.
