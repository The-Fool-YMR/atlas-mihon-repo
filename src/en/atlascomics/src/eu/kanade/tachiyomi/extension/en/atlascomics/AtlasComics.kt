package eu.kanade.tachiyomi.extension.en.atlascomics

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonElement
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

@Source
abstract class AtlasComics : KeiSource() {

    private val websiteUrl = "https://atlascomics.dpdns.org"

    // The image proxy rejects requests whose Referer is the API host.
    override fun Headers.Builder.configureHeaders() = set("Referer", "$websiteUrl/").set("Origin", websiteUrl)

    override fun getHomeUrl() = websiteUrl

    override fun getMangaUrl(manga: SManga) = "$websiteUrl/series/${manga.url.substringAfterLast('/')}"

    override fun OkHttpClient.Builder.configureClient() = rateLimit(2).readTimeout(60, TimeUnit.SECONDS)

    override suspend fun getPopularManga(page: Int): MangasPage = browse("trending", page)

    private suspend fun browse(endpoint: String, page: Int): MangasPage {
        val url = "$baseUrl/v1/$endpoint".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .addQueryParameter("limit", "50")
            .addQueryParameter("free_only", "true")
            .build()
        val result = client.get(url, Fetch.XHR).parseAs<CatalogResponse>()
        return MangasPage(result.data.map { it.toSManga() }, result.hasNext)
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        if (page > 1) return MangasPage(emptyList(), false)
        val result = client.get("$baseUrl/v1/latest?limit=100&free_only=true", Fetch.XHR)
            .parseAs<LatestResponse>()
        return MangasPage(result.items.distinctBy { it.seriesId }.map { it.toSManga() }, false)
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val parameters = filters.mapNotNull {
            when (it) {
                is TextFilter -> it.parameter to it.state.trim()
                is SelectFilter -> it.parameter to it.value
                else -> null
            }
        }.filter { it.second.isNotEmpty() }
        if (query.isBlank() && parameters.isEmpty()) {
            return browse(filters.firstInstanceOrNull<BrowseFilter>()?.endpoint ?: "trending", page)
        }
        val url = "$baseUrl/v1/search".toHttpUrl().newBuilder()
            .addQueryParameter("q", query.trim())
            .apply { parameters.forEach { (key, value) -> addQueryParameter(key, value) } }
            .build()
        // Search has no server-side pagination; omit limit to avoid silently truncating results.
        val results = client.get(url, Fetch.XHR).parseAs<SearchResponse>().results
        val offset = (page - 1) * 50
        return MangasPage(results.drop(offset).take(50).map { it.toSManga() }, results.size > offset + 50)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        val segments = url.pathSegments
        val id = when {
            url.host == baseUrl.toHttpUrl().host && segments.take(2) == listOf("v1", "series") -> segments.getOrNull(2)
            url.host == websiteUrl.toHttpUrl().host && segments.firstOrNull() == "series" ->
                segments.getOrNull(1)?.substringAfterLast('-')
            else -> null
        }?.takeIf { it.isNotBlank() } ?: return null
        return client.get("$baseUrl/v1/series/$id", Fetch.XHR).parseAs<SeriesDetails>().toSManga()
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = coroutineScope {
        val details = async {
            if (fetchDetails) {
                client.get("$baseUrl${manga.url}", Fetch.XHR).parseAs<SeriesDetails>().toSManga()
            } else {
                manga
            }
        }
        val chapterList = async {
            if (fetchChapters) {
                client.get("$baseUrl${manga.url}/chapters?free_only=true", Fetch.XHR)
                    .parseAs<ChapterResponse>().toChapters()
            } else {
                chapters
            }
        }
        SMangaUpdate(manga = details.await(), chapters = chapterList.await())
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> = client.get(getChapterUrl(chapter), Fetch.XHR).parseAs<PagesResponse>().toPages()

    override fun getFilterList(data: JsonElement?) = atlasFilters()
}
