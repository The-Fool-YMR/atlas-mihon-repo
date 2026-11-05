# Atlas Comics for Mihon

An English Mihon source extension for [Atlas Comics](https://atlascomics.dpdns.org), using its [public API](https://api.atlascomics.dpdns.org/docs) and the [Keiyoushi extension framework](https://github.com/keiyoushi/extensions-source).

## Features

- Trending catalog, all titles, recently added, recently completed, and most followed browsing.
- Latest updates, deduplicated by series. Atlas exposes up to 100 latest entries without pagination.
- Title search and filters for type, status, demographic, content rating, genres, themes, tags, author, artist, publisher, and year. Search results are paginated locally because Atlas's search endpoint has no page parameter.
- Series descriptions, covers, authors, artists, genres, status, and alternative titles.
- English, unlocked chapters from every available source, labeled using Mihon's scanlator field. Different sources retain distinct chapter URLs.
- Decimal chapters, descending chapter order, upload dates, and page images in numeric order.
- Atlas website and API series links accepted in URL search. Images use the website Referer required by Atlas's proxy.

## Install

Add this URL in Mihon's extension stores/repositories:

```text
https://github.com/The-Fool-YMR/atlas-mihon-repo/raw/repo/index.pb
```

Refresh the extension list, then install **Atlas Comics**. The catalog is hosted on this GitHub repository's `repo` branch; buildable source lives on `main`.

Transfer `dist/atlas-comics-v1.6.1.apk` to your Android device and install it. In Mihon, open **Browse → Extensions**, locate **Atlas Comics**, and trust the extension if prompted. Then open **Browse → Sources → English → Atlas Comics**.

This project publishes its own catalog. Builds use Android's development signing key unless you supply your own signing configuration; retain the same key for future updates. The catalog's fingerprint is extracted from the actual signed APK. If you installed the older **ATLAS** build previously hosted here, export your library backup and migrate to **Atlas Comics**; the old build used a different signing key and source ID. The APK built in this chat remains compatible with the new catalog.

## Build on Windows

Requires Git, JDK 21+, and Android SDK platform **37.0** with build-tools **36.0.0** (the pinned framework's default). Set `JAVA_HOME` and `ANDROID_HOME`, then run:

```powershell
.\build.ps1
```

The script downloads a sparse checkout of Keiyoushi pinned to commit `4591fbc9bb571ad03aaea0f7428f7f403350e90e`, copies the extension module into it, formats the Kotlin source, builds the release APK, runs unit tests and release lint, and copies the result to `dist/`. Portable tools already present in `.tools/jdk` and `.tools/android-sdk` are detected automatically.

The GitHub Actions workflow builds and uploads the APK when this project is pushed to your own repository. No publishing or external repository changes are performed by the local build script.

## Regenerate the GitHub catalog

Install `repository/requirements.txt` in a Python virtual environment. Run `repository/generate_catalog.py --help` for its build metadata, APK, optional JAR, icon, Android tools, output directory, and public base URL arguments. The generator verifies the APK signature and package/version against the build metadata before writing gzip-compressed `index.pb`, JSON indexes, `repo.json`, download files, and checksums. Publish the generated directory's contents to the `repo` branch. Keep the signing key unchanged when publishing updates.

## Source and checks

The extension module lives in `src/en/atlascomics`. Its `build.gradle.kts` uses the current `KeiSource` API (`libVersion = "1.6"`) with generated source registration. It can also be copied into the pinned Keiyoushi checkout directly.

`tests/fixtures` contains public API responses captured on November 5, 2026. Unit tests cover response parsing, stable series identifiers, multiple source versions, chapter sorting, locked/language filtering, decimal chapters, dates, and numeric page order. Test-only in-memory manga and chapter models supply the host factories that the extension SDK deliberately leaves as throwing stubs. These doubles are outside the production source set and are not packaged in the APK. These checks do not replace testing the installed APK on a device running Mihon.

The API and image servers must remain reachable. Locked chapters are omitted; this extension does not implement Atlas account login or purchases.

## License

Extension code is licensed under Apache-2.0; see `LICENSE`. The app icon is supplied by Atlas Comics and remains the property of its owner. Keiyoushi's downloaded framework retains its own license and attribution.
