package eu.kanade.tachiyomi.extension.en.atlascomics

import okhttp3.HttpUrl
import okhttp3.Request

internal fun HttpUrl.isAtlasApi(server: HttpUrl) = scheme == server.scheme && host == server.host && port == server.port &&
    encodedPath.startsWith("/v1/")

internal fun HttpUrl.acceptsAtlasKey(server: HttpUrl) = isHttps && isAtlasApi(server)

internal fun Request.withAtlasKey(server: HttpUrl, key: String): Request = newBuilder()
    .removeHeader("X-API-Key")
    .apply {
        if (url.acceptsAtlasKey(server) && key.isNotEmpty()) header("X-API-Key", key)
    }
    .build()
