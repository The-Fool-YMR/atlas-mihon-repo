package eu.kanade.tachiyomi.source.model

import kotlinx.serialization.json.JsonObject

// The extension SDK's factories throw "Stub!" outside Mihon. These test-only
// models supply the host factories while exercising the real DTO mapping code.
interface SManga {
    var url: String
    var title: String
    var thumbnail_url: String?
    var author: String?
    var artist: String?
    var description: String?
    var genre: String?
    var status: Int

    companion object {
        const val UNKNOWN = 0
        const val ONGOING = 1
        const val COMPLETED = 2
        const val CANCELLED = 5
        const val ON_HIATUS = 6

        fun create(): SManga = object : SManga {
            override var url = ""
            override var title = ""
            override var thumbnail_url: String? = null
            override var author: String? = null
            override var artist: String? = null
            override var description: String? = null
            override var genre: String? = null
            override var status = UNKNOWN
        }
    }
}

interface SChapter {
    var url: String
    var name: String
    var chapter_number: Float
    var scanlator: String?
    var date_upload: Long
    var memo: JsonObject

    companion object {
        fun create(): SChapter = object : SChapter {
            override var url = ""
            override var name = ""
            override var chapter_number = -1f
            override var scanlator: String? = null
            override var date_upload = 0L
            override var memo = JsonObject(emptyMap())
        }
    }
}
