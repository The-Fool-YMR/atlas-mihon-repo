package eu.kanade.tachiyomi.extension.en.atlascomics

import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.parseAs
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.BeforeClass
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.addSingleton

class AtlasComicsTest {
    companion object {
        @BeforeClass
        @JvmStatic
        fun setup() {
            Injekt.addSingleton(Json { ignoreUnknownKeys = true })
        }
    }

    private inline fun <reified T> fixture(name: String): T =
        javaClass.getResourceAsStream("/$name")!!.bufferedReader().use { it.readText() }.parseAs<T>()

    @Test
    fun catalogSearchAndLatestKeepTheSameSeriesIdentity() {
        val catalog = fixture<CatalogResponse>("atlas-series.json")
        assertTrue(catalog.hasNext)
        assertEquals("/v1/series/FEopuDBW04AS", catalog.data.first().toSManga().url)
        assertTrue(catalog.data.first().toSManga().thumbnail_url!!.startsWith("https://"))
        val search = fixture<SearchResponse>("atlas-search.json")
        assertEquals("Solo Bug Player", search.results.first().toSManga().title)
        val latest = fixture<LatestResponse>("atlas-latest.json").items.first().toSManga()
        val details = fixture<SeriesDetails>("atlas-detail.json").toSManga()
        assertEquals(latest.url, details.url)
        assertEquals(latest.title, details.title)
        assertEquals(SManga.ONGOING, details.status)
        assertTrue(details.author!!.contains("HEINIAOSHE"))
        assertTrue(details.artist!!.contains("Ke Xin"))
    }

    @Test
    fun chapterVersionsRemainSeparateAndSortNumerically() {
        val chapters = fixture<ChapterResponse>("atlas-chapters.json").toChapters()
        assertTrue(chapters.size > 300)
        assertTrue(chapters.zipWithNext().all { (a, b) -> a.chapter_number >= b.chapter_number })
        val firstChapter = chapters.filter { it.chapter_number == 1f }
        assertTrue(firstChapter.size > 1)
        assertEquals(firstChapter.size, firstChapter.map { it.url }.distinct().size)
        assertTrue(firstChapter.map { it.scanlator }.distinct().size > 1)
        assertTrue(chapters.all { it.date_upload > 0 })
    }

    @Test
    fun lockedAndNonEnglishChaptersAreExcludedWithoutLosingDecimalChapters() {
        val json = """{"groups":[{"sources":[
            {"chapter_url":"/v1/series/a/chapter-10.5-a","chapter_number":"10.5","title":"","source_name":"A","is_locked":false,"language":"en","updated_at":"2026-11-01T00:00:00Z"},
            {"chapter_url":"/v1/series/a/chapter-11-b","chapter_number":"11","title":"Paid","source_name":"B","is_locked":true},
            {"chapter_url":"/v1/series/a/chapter-12-c","chapter_number":"12","title":"Other language","source_name":"C","is_locked":false,"language":"es"}
        ]}]}"""
        val chapters = json.parseAs<ChapterResponse>().toChapters()
        assertEquals(1, chapters.size)
        assertEquals(10.5f, chapters.single().chapter_number, 0f)
        assertEquals("Chapter 10.5", chapters.single().name)
        assertTrue(chapters.single().date_upload > 0)
    }

    @Test
    fun pageOrderUsesIndexAndKeepsOneSource() {
        val pages = fixture<PagesResponse>("atlas-chapter.json").toPages()
        assertEquals(23, pages.size)
        assertTrue(pages.all { it.imageUrl!!.contains("/manhwanex/") })
        val shuffled = """{"pages":[{"index":10,"url":"https://img.example/10"},{"index":2,"url":"https://img.example/2"},{"index":0,"url":"https://img.example/0"}]}"""
            .parseAs<PagesResponse>().toPages()
        assertEquals(listOf(0, 2, 10), shuffled.map { it.index })
        assertFalse(shuffled.any { it.imageUrl.isNullOrBlank() })
        assertTrue("""{"pages":[]}""".parseAs<PagesResponse>().toPages().isEmpty())
    }
}
