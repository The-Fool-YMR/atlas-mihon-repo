# thefool — Mihon Extension Store

Read **Atlas Comics** in [Mihon](https://mihon.app/).

| What you see in Mihon | Name |
| --- | --- |
| Extension store / publisher | **thefool** |
| Extension and reading source | **Atlas Comics** |
| Chapter language | English |

## Add the store

1. Open **Mihon → More → Settings → Browse → Extension stores**.
2. Tap **Add** or **+**.
3. Paste the complete URL below and confirm.

```text
https://raw.githubusercontent.com/The-Fool-YMR/atlas-mihon-repo/repo/index.pb
```

[Open the repository index](https://raw.githubusercontent.com/The-Fool-YMR/atlas-mihon-repo/repo/index.pb)

The store should appear as **thefool**.

## Install Atlas Comics

1. Go to **Browse → Extensions**.
2. Refresh the list and search for **Atlas Comics**.
3. Tap **Install** and complete any installation prompts.
4. If Mihon marks the extension as untrusted, select **Trust** for the Atlas Comics extension from this store.
5. Open **Browse → Sources → English → Atlas Comics**.

Use a Mihon version that supports **extension API 1.6**. If Mihon reports an unsupported extension version, update Mihon using its [official website](https://mihon.app/).

## Find and read a series

- Open Atlas Comics to browse trending titles.
- Use **Latest** to see recent chapter updates.
- Search by title, or open the filters to browse all titles, recently added, recently completed, or most followed series.
- Filter by type, status, demographic, content rating, genres, themes, tags, author, artist, publisher, or year.
- Open a series, add it to your library, and choose a chapter to start reading.

A series can have chapter versions from several sources. The chapter's scanlator label identifies its source, so multiple versions of the same chapter number are expected.

The extension lists English chapters provided by Atlas Comics. Locked chapters are hidden by default. Chapter and image availability depends on Atlas Comics and its upstream sources.

## Extension settings

In **Browse → Extensions**, tap the gear beside **Atlas Comics**.

| Setting | What it does |
| --- | --- |
| **API key** | Paste your personal Atlas key to use its higher request allowance. Leave it blank for public access. The field masks the key, and the extension sends it only to the configured HTTPS API server. |
| **Custom Base URL** | Set the Atlas API server, including its scheme and port if needed. Clear the field to restore `https://api.atlascomics.dpdns.org`. HTTP servers can be used for testing if Mihon permits cleartext connections, but do not receive your API key. |
| **Show locked chapters** | Show locked chapters with a 🔒 next to the title. They remain unreadable. Refresh the series after changing this setting. |

## Updates

When a new extension version is published, refresh **Browse → Extensions** and install the available update. Keep the **thefool** store added to receive its extension listings.

To check for new chapters in a series, refresh that series in Mihon or use Mihon's library update feature.

## Manual APK download

If you need to install the current APK directly:

[**Download Atlas Comics v1.6.2**](https://raw.githubusercontent.com/The-Fool-YMR/atlas-mihon-repo/repo/apk/atlas-comics-v1.6.2.apk)

Open the downloaded APK on your Android device and complete installation. If Android asks, allow installation from the app you used to open it. Then return to Mihon's Extensions tab and trust the extension if prompted.

Use the file from this link, rather than a previously downloaded copy of the old ATLAS extension.

## Troubleshooting

| Problem | What to try |
| --- | --- |
| Adding the store shows HTTP 404 | Copy the full `raw.githubusercontent.com` URL from **Add the store** above. |
| The store still says “Atlas Comics Extensions” | Open **Extension stores**, tap **Refresh**, then restart Mihon. If needed, remove and re-add the store using the URL above. |
| Atlas Comics does not appear | Refresh Extensions and check the English-language and content-rating filters. This extension is marked **Mixed**. |
| Download or installation fails | Retry with the current APK link above. If the old ATLAS extension is still installed and Android reports a package/signature conflict, back up your Mihon library before removing the old extension and installing the new one. |
| Chapters or images fail to load | Check your connection and whether the same series opens on the [Atlas Comics website](https://atlascomics.dpdns.org). Retry when the source is available. |

If a problem continues, [report it on GitHub](https://github.com/The-Fool-YMR/atlas-mihon-repo/issues). Include your Mihon version, Android version, the exact error, and the affected series/chapter if relevant.

## Links and credits

- [Atlas Comics website](https://atlascomics.dpdns.org)
- [Mihon](https://mihon.app/)
- [Extension source code](https://github.com/The-Fool-YMR/atlas-mihon-repo/tree/main/src/en/atlascomics)
- [Developer and build guide](https://github.com/The-Fool-YMR/atlas-mihon-repo/blob/main/DEVELOPMENT.md)

Extension code is licensed under [Apache-2.0](https://github.com/The-Fool-YMR/atlas-mihon-repo/blob/main/LICENSE). The extension uses the [Keiyoushi build framework](https://github.com/keiyoushi/extensions-source). The Atlas Comics icon belongs to its owner.

