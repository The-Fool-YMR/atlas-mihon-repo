package eu.kanade.tachiyomi.extension.en.atlascomics

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList

class BrowseFilter : Filter.Select<String>("Browse", arrayOf("Trending", "All titles", "Recently added", "Recently completed", "Most followed")) {
    val endpoint: String
        get() = arrayOf("trending", "series", "recently-added", "recently-completed", "most-followed")[state]
}

class TextFilter(name: String, val parameter: String) : Filter.Text(name)

class SelectFilter(name: String, val parameter: String, private val options: Array<String>) : Filter.Select<String>(name, arrayOf("Any", *options)) {
    val value: String
        get() = if (state == 0) "" else options[state - 1]
}

fun atlasFilters() = FilterList(
    BrowseFilter(),
    Filter.Header("Browse applies when title and metadata filters are empty."),
    Filter.Separator(),
    SelectFilter("Type", "type", arrayOf("manga", "manhwa", "manhua")),
    SelectFilter("Status", "status", arrayOf("ongoing", "completed", "hiatus", "cancelled")),
    SelectFilter("Demographic", "demographic", arrayOf("Shounen", "Shoujo", "Seinen", "Josei")),
    SelectFilter("Content rating", "content_rating", arrayOf("safe", "suggestive", "erotica", "pornographic")),
    TextFilter("Genres (comma separated)", "genres"),
    TextFilter("Themes (comma separated)", "themes"),
    TextFilter("Tags (comma separated)", "tags"),
    TextFilter("Author", "author"),
    TextFilter("Artist", "artist"),
    TextFilter("Publisher", "publisher"),
    TextFilter("Year", "year"),
)
