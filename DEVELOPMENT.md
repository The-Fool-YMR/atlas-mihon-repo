# Atlas Comics — Development

For installation and reading in Mihon, see [README.md](README.md).

## Build on Windows

Requires Git, JDK 21+, and Android SDK platform **37.0** with build-tools **36.0.0** (the pinned framework's default). Set `JAVA_HOME` and `ANDROID_HOME`, then run:

```powershell
.\build.ps1
```

The script downloads a sparse checkout of Keiyoushi pinned to commit `4591fbc9bb571ad03aaea0f7428f7f403350e90e`, copies the extension module into it, formats the Kotlin source, builds the release APK, runs unit tests and release lint, and copies the result to `dist/`. Portable tools already present in `.tools/jdk` and `.tools/android-sdk` are detected automatically.

Build and test the APK manually with `build.ps1`. No publishing or external repository changes are performed by the local build script.

## Regenerate the GitHub catalog

Install `repository/requirements.txt` in a Python virtual environment. Run `repository/generate_catalog.py --help` for its build metadata, APK, optional JAR, icon, Android tools, output directory, and public base URL arguments. The generator verifies the APK signature and package/version against the build metadata before writing gzip-compressed `index.pb`, JSON indexes, `repo.json`, download files, and checksums. Publish the generated directory's contents to the `repo` branch. Keep the signing key unchanged when publishing updates.

## Source and checks

The extension module lives in `src/en/atlascomics`. Its `build.gradle.kts` uses the current `KeiSource` API (`libVersion = "1.6"`) with generated source registration. It can also be copied into the pinned Keiyoushi checkout directly.

`tests/fixtures` contains public API responses captured on November 5, 2026. Unit tests cover response parsing, stable series identifiers, multiple source versions, chapter sorting, locked/language filtering, decimal chapters, dates, and numeric page order. Test-only in-memory manga and chapter models supply the host factories that the extension SDK deliberately leaves as throwing stubs. These doubles are outside the production source set and are not packaged in the APK. These checks do not replace testing the installed APK on a device running Mihon.

The API and image servers must remain reachable. Locked chapters are omitted; this extension does not implement Atlas account login or purchases.

## License

Extension code is licensed under Apache-2.0; see `LICENSE`. The app icon is supplied by Atlas Comics and remains the property of its owner. Keiyoushi's downloaded framework retains its own license and attribution.
