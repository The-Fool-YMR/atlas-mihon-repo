package eu.kanade.tachiyomi.extension.en.atlascomics

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import okhttp3.Headers
import okhttp3.Request
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * ATLAS — Mihon / Tachiyomi source for atlascomics.fun.
 *
 * This source does NOT scrape the website. It talks to the site's own
 * public JSON API at api.atlascomics.fun, which is what the ATLAS site
 * itself reads. That is why the parsing below is JSON, not HTML selectors.
 *
 * API mapping (all verified against the live API):
 *   Browse (Popular)  GET /v1/series?page={page}&limit=25
 *                      -> { "data": [...], "has_next": bool }
 *   Latest updates     GET /v1/latest?limit=25&free_only=true
 *                      -> { "items": [...] }  (no pagination on the API side)
 *   Search             GET /v1/search?q={query}
 *                      -> { "results": [...] }  (no pagination on the API side)
 *   Series details     GET /v1/series/{uid}?free_only=true
 *   Chapter list       GET /v1/series/{uid}/chapters?free_only=true
 *                      -> { "groups": [{ "chapter_number", "sources": [...] }] }
 *   Page list          GET /v1/chapters/{chapter_id}/pages
 *                      -> { "pages": [{ "index", "url" }] }
 *   Covers             R2 covers arrive as full URLs; ComicK covers
 *                      arrive as a bare b2key filename, which
 *                      resolveCover() prefixes with
 *                      https://meo.comick.pictures/ (see that function).
 *
 * Reading rules, matching the ATLAS site:
 *   - free_only=true everywhere, so locked (paid) chapters are never
 *     listed in Mihon, exactly like the site hides them.
 *   - A chapter's date is freed_at when present, otherwise updated_at
 *     (the site shows freed_at as the chapter time).
 *   - One Mihon chapter per (chapter number, scanlator source), so a
 *     chapter available from Asura, FlameComics and Qimanga shows as
 *     three entries grouped by scanlator, and the reader can pick one.
 *     The Mihon chapter's url is the ATLAS chapter id, which is what
 *     the pages endpoint needs.
 */
class AtlasComics : HttpSource() {

    override val name = "ATLAS"

    override val baseUrl = "https://api.atlascomics.fun"

    override val lang = "en"

    /**
     * Permanent ATLAS source id. HttpSource normally auto-generates
     * this, but it is pinned here so it can NEVER drift from the id
     * in the extension repo's index (atlas-mihon-repo/index.json):
     * Mihon uses the id to match library entries and backups to
     * this source, so changing it would orphan existing libraries.
     * Value = FNV-1a 64-bit hash of the package name
     * "eu.kanade.tachiyomi.extension.en.atlascomics"
     * (= 2685846169743944013). Never change it.
     */
    override val id = 2685846169743944013L

    override val supportsLatest = true

    // Cloudflare in front of the API rejects bare script user-agents
    // (a plain python UA got a 403 while a browser UA worked), so
    // identify as a mobile browser. This header is also used by Mihon
    // when it downloads page images.
    override fun headersBuilder(): Headers.Builder = Headers.Builder()
        .add("User-Agent", "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36")
        .add("Accept", "application/json")

    // ------------------------------------------------------------------
    // Browse (Popular)
    // ------------------------------------------------------------------

    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl/v1/series?page=$page&limit=25", headers)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val json = JSONObject(response.body.string())
        val mangas = json.getJSONArray("data").map { it.toSeries() }
        return MangasPage(mangas, json.optBoolean("has_next", false))
    }

    // ------------------------------------------------------------------
    // Latest updates
    //
    // /v1/latest has no page parameter, so only page 1 returns items.
    // latestUpdatesRequest records the requested page and the parser
    // returns an empty page for anything past 1, instead of repeating
    // the same 25 items forever.
    // ------------------------------------------------------------------

    private var latestPage = 1

    override fun latestUpdatesRequest(page: Int): Request {
        latestPage = page
        return GET("$baseUrl/v1/latest?limit=25&free_only=true", headers)
    }

    override fun latestUpdatesParse(response: Response): MangasPage {
        if (latestPage > 1) return MangasPage(emptyList(), false)
        val json = JSONObject(response.body.string())
        val mangas = json.getJSONArray("items").map { item ->
            SManga.create().apply {
                url = item.getString("series_id")
                title = item.getString("series_title")
                thumbnail_url = resolveCover(item.optString("cover_thumb"))
            }
        }
        return MangasPage(mangas, false)
    }

    // ------------------------------------------------------------------
    // Search
    //
    // /v1/search has no pagination either, so the same page-1-only
    // rule applies as for latest updates.
    // ------------------------------------------------------------------

    private var searchPage = 1

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        searchPage = page
        val encoded = query.trim().replace(" ", "%20")
        return GET("$baseUrl/v1/search?q=$encoded", headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        if (searchPage > 1) return MangasPage(emptyList(), false)
        val json = JSONObject(response.body.string())
        val mangas = json.getJSONArray("results").map { it.toSeries() }
        return MangasPage(mangas, false)
    }

    // ------------------------------------------------------------------
    // Series details
    // ------------------------------------------------------------------

    override fun mangaDetailsRequest(manga: SManga): Request {
        // SManga.url is the ATLAS series uid (see toSeries() below).
        return GET("$baseUrl/v1/series/${manga.url}?free_only=true", headers)
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val json = JSONObject(response.body.string())
        val titles = json.optJSONObject("titles")
        return SManga.create().apply {
            url = json.getString("uid")
            title = titles?.optString("display").orEmpty()
                .ifEmpty { titles?.optString("en").orEmpty() }
            description = json.optString("description_en").nullIfEmpty()
            author = json.optJSONArray("authors")?.joinNames()
            artist = json.optJSONArray("artists")?.joinNames()
            genre = (
                (json.optJSONArray("genres")?.toStringList() ?: emptyList()) +
                    (json.optJSONArray("themes")?.toStringList() ?: emptyList()) +
                    (json.optJSONArray("tags")?.toStringList() ?: emptyList())
                ).joinToString(", ").nullIfEmpty()
            status = when (json.optString("status").lowercase(Locale.US)) {
                "ongoing", "releasing" -> SManga.ONGOING
                "completed" -> SManga.COMPLETED
                "hiatus" -> SManga.HIATUS
                "cancelled", "canceled" -> SManga.CANCELLED
                else -> SManga.UNKNOWN
            }
            val cover = json.optJSONObject("cover")
            thumbnail_url = resolveCover(
                when {
                    cover == null -> null
                    cover.optString("thumb_url").isNotEmpty() -> cover.optString("thumb_url")
                    cover.optString("url").isNotEmpty() -> cover.optString("url")
                    else -> null
                },
            )
            initialized = true
        }
    }

    // ------------------------------------------------------------------
    // Chapter list
    //
    // Uses the grouped endpoint rather than the detail's chapter
    // preview, so long series get their full chapter list. One
    // SChapter per source entry; locked entries are already removed
    // by free_only=true on the request.
    // ------------------------------------------------------------------

    override fun chapterListRequest(manga: SManga): Request {
        return GET("$baseUrl/v1/series/${manga.url}/chapters?free_only=true", headers)
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val json = JSONObject(response.body.string())
        val chapters = mutableListOf<SChapter>()
        val groups = json.getJSONArray("groups")
        for (i in 0 until groups.length()) {
            val group = groups.getJSONObject(i)
            val numberText = group.getString("chapter_number")
            val number = numberText.toFloatOrNull() ?: 0f
            val sources = group.getJSONArray("sources")
            for (j in 0 until sources.length()) {
                val source = sources.getJSONObject(j)
                if (source.optBoolean("is_locked", false)) continue
                chapters.add(
                    SChapter.create().apply {
                        // The ATLAS chapter id: pageListRequest uses it
                        // directly against /v1/chapters/{id}/pages.
                        url = source.getString("chapter_id")
                        name = "Chapter $numberText"
                        chapter_number = number
                        scanlator = source.optString("source_name")
                            .ifEmpty { source.optString("source_id") }
                        date_upload = parseDate(
                            source.optString("freed_at")
                                .ifEmpty { source.optString("updated_at") },
                        )
                    },
                )
            }
        }
        // Newest first, matching the site's chapter list.
        return chapters.sortedWith(
            compareByDescending<SChapter> { it.chapter_number }
                .thenByDescending { it.date_upload },
        )
    }

    // ------------------------------------------------------------------
    // Page list
    // ------------------------------------------------------------------

    override fun pageListRequest(chapter: SChapter): Request {
        return GET("$baseUrl/v1/chapters/${chapter.url}/pages", headers)
    }

    override fun pageListParse(response: Response): List<Page> {
        val json = JSONObject(response.body.string())
        val pages = json.getJSONArray("pages")
        return (0 until pages.length()).map { i ->
            val page = pages.getJSONObject(i)
            Page(page.optInt("index", i), imageUrl = page.getString("url"))
        }
    }

    override fun imageUrlParse(response: Response): String {
        throw UnsupportedOperationException("Not used: page image URLs come from pageListParse")
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    /** One ATLAS list/search item -> one Mihon series. */
    private fun JSONObject.toSeries(): SManga {
        return SManga.create().apply {
            url = getString("uid")
            title = getString("title")
            // List items use cover_thumb; search items use cover_thumb_url.
            thumbnail_url = resolveCover(
                optString("cover_thumb").ifEmpty { optString("cover_thumb_url") },
            )
            author = optJSONArray("authors")?.joinNames()
            artist = optJSONArray("artists")?.joinNames()
            genre = optJSONArray("genres")?.toStringList()
                ?.joinToString(", ").nullIfEmpty()
            status = when (optString("status").lowercase(Locale.US)) {
                "ongoing", "releasing" -> SManga.ONGOING
                "completed" -> SManga.COMPLETED
                "hiatus" -> SManga.HIATUS
                "cancelled", "canceled" -> SManga.CANCELLED
                else -> SManga.UNKNOWN
            }
        }
    }

    private fun JSONArray.toStringList(): List<String> {
        return (0 until length()).mapNotNull { i -> optString(i).nullIfEmpty() }
    }

    private fun JSONArray.joinNames(): String? {
        return toStringList().joinToString(", ").nullIfEmpty()
    }

    private fun <T> JSONArray.map(transform: (JSONObject) -> T): List<T> {
        return (0 until length()).map { i -> transform(getJSONObject(i)) }
    }

    private fun String?.nullIfEmpty(): String? = if (this.isNullOrEmpty()) null else this

    /**
     * Cover values are not always absolute URLs. Series with an R2
     * cover come back as a full https://cdn.atlascomics.fun/... URL,
     * but ComicK-linked series without an R2 cover come back as just
     * the ComicK md_covers b2key filename (e.g. "brBYNW.png") — the
     * API itself prefixes that key with https://meo.comick.pictures/
     * when it builds covers elsewhere. Mihon needs an absolute URL,
     * so a bare filename gets the same prefix here.
     */
    private fun resolveCover(raw: String?): String? {
        val value = raw.nullIfEmpty() ?: return null
        if (value.startsWith("http://") || value.startsWith("https://")) return value
        return "https://meo.comick.pictures/$value"
    }

    /** ATLAS timestamps are ISO-8601 UTC ("2023-09-01T09:12:55.000Z"). */
    private fun parseDate(raw: String): Long {
        if (raw.isBlank()) return 0L
        val formats = listOf(
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
            "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
            "yyyy-MM-dd'T'HH:mm:ssXXX",
        )
        for (pattern in formats) {
            try {
                return SimpleDateFormat(pattern, Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }.parse(raw)?.time ?: 0L
            } catch (_: Exception) {
                // Try the next pattern.
            }
        }
        return 0L
    }
}
