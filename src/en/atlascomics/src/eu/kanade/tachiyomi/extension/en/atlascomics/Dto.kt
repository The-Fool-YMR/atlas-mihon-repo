package eu.kanade.tachiyomi.extension.en.atlascomics

import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.MarkdownDescription
import keiyoushi.utils.tryParse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlin.time.Instant

@Serializable
class CatalogResponse(val data: List<CatalogSeries>, @SerialName("has_next") val hasNext: Boolean)

@Serializable
class SearchResponse(val results: List<CatalogSeries>)

@Serializable
class CatalogSeries(
    private val uid: String,
    private val title: String,
    @SerialName("cover_thumb") private val coverThumb: String? = null,
    @SerialName("cover_fallback_url") private val coverFallbackUrl: String? = null,
) {
    fun toSManga() = SManga.create().apply {
        url = "/v1/series/$uid"
        title = this@CatalogSeries.title
        thumbnail_url = coverThumb?.takeIf { it.isNotBlank() } ?: coverFallbackUrl
    }
}

@Serializable
class LatestResponse(val items: List<LatestSeries>)

@Serializable
class LatestSeries(
    @SerialName("series_id") val seriesId: String,
    @SerialName("series_title") private val title: String,
    @SerialName("cover_thumb") private val coverThumb: String? = null,
    @SerialName("cover_fallback_url") private val coverFallbackUrl: String? = null,
) {
    fun toSManga() = SManga.create().apply {
        url = "/v1/series/$seriesId"
        title = this@LatestSeries.title
        thumbnail_url = coverThumb?.takeIf { it.isNotBlank() } ?: coverFallbackUrl
    }
}

@Serializable
class SeriesDetails(
    private val uid: String,
    private val titles: Titles,
    @SerialName("description_en") private val descriptionEn: String? = null,
    private val status: String? = null,
    private val type: String? = null,
    private val year: Int? = null,
    private val genres: List<String> = emptyList(),
    private val themes: List<String> = emptyList(),
    private val formats: List<String> = emptyList(),
    private val authors: List<Person> = emptyList(),
    private val artists: List<Person> = emptyList(),
    private val cover: Cover? = null,
    @SerialName("cover_fallback_url") private val coverFallbackUrl: String? = null,
) {
    fun toSManga() = SManga.create().apply {
        url = "/v1/series/$uid"
        title = titles.display
        author = authors.joinToString { it.name }.ifBlank { null }
        artist = artists.joinToString { it.name }.ifBlank { null }
        description = MarkdownDescription.Builder()
            .description(descriptionEn)
            .info(year?.toString(), type)
            .altTitles(titles.alt)
            .build()
        genre = (genres + themes + formats).distinct().joinToString().ifBlank { null }
        thumbnail_url = cover?.thumbUrl?.takeIf { it.isNotBlank() }
            ?: cover?.url?.takeIf { it.isNotBlank() } ?: coverFallbackUrl
        status = when (this@SeriesDetails.status?.lowercase()) {
            "ongoing" -> SManga.ONGOING
            "completed" -> SManga.COMPLETED
            "hiatus" -> SManga.ON_HIATUS
            "cancelled", "canceled" -> SManga.CANCELLED
            else -> SManga.UNKNOWN
        }
    }
}

@Serializable
class Titles(val display: String, val alt: List<String> = emptyList())

@Serializable
class Person(val name: String)

@Serializable
class Cover(val url: String? = null, @SerialName("thumb_url") val thumbUrl: String? = null)

@Serializable
class ChapterResponse(private val groups: List<ChapterGroup>) {
    fun toChapters(showLocked: Boolean = false): List<SChapter> = groups.flatMap { it.sources }
        .filter { (showLocked || !it.isLocked) && (it.language == null || it.language == "en") }
        .map { it.toSChapter() }
        .distinctBy { it.url }
        .sortedByDescending { it.chapter_number }
}

@Serializable
class ChapterGroup(val sources: List<Chapter>)

@Serializable
class Chapter(
    @SerialName("chapter_url") private val chapterUrl: String,
    @SerialName("chapter_number") private val chapterNumber: String,
    private val title: String? = null,
    @SerialName("source_name") private val sourceName: String,
    @SerialName("scanlator_group") private val scanlatorGroup: String? = null,
    @SerialName("is_locked") val isLocked: Boolean,
    val language: String? = null,
    @SerialName("updated_at") private val updatedAt: String? = null,
    @SerialName("freed_at") private val freedAt: String? = null,
) {
    fun toSChapter() = SChapter.create().apply {
        url = chapterUrl
        chapter_number = chapterNumber.toFloatOrNull() ?: -1f
        name = title?.takeIf { it.isNotBlank() } ?: "Chapter ${chapterNumber.removeSuffix(".0")}"
        if (isLocked) {
            name = "🔒 $name"
            memo = buildJsonObject { put("atlas_locked", JsonPrimitive(true)) }
        }
        scanlator = listOfNotNull(sourceName, scanlatorGroup?.takeIf { it.isNotBlank() }).distinct().joinToString(" / ")
        date_upload = Instant.tryParse(freedAt ?: updatedAt)
    }
}

@Serializable
class PagesResponse(private val pages: List<ChapterPage>) {
    @SerialName("is_locked")
    private val isLocked: Boolean = false

    fun toPages(): List<Page> {
        if (isLocked) error("This chapter is locked on Atlas.")
        return pages.sortedBy { it.index }.map { Page(it.index, imageUrl = it.url) }
    }
}

@Serializable
class ChapterPage(val index: Int, val url: String)
