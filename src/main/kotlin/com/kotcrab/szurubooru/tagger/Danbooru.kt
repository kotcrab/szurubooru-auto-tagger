package com.kotcrab.szurubooru.tagger

import com.github.salomonbrys.kotson.*
import com.google.gson.JsonElement
import org.apache.commons.codec.binary.Base64
import org.jsoup.HttpStatusException
import java.util.*

/**
 * Simple Danbooru client, only method and models required for auto-tagger are implemented.
 * @author Kotcrab
 */
class Danbooru(private val config: DanbooruDto) {
    companion object {
        val URL_BASE = "danbooru.donmai.us/"
        val URL = "https://" + URL_BASE
        val POSTS_URL = URL_BASE + "posts/"
    }

    private val restClient by lazy {
        if (config.anonymous == false) {
            val login: String = "${config.username}:${config.apiKey}"
            val basicHttpAuth = String(Base64.encodeBase64(login.toByteArray(charset = Charsets.US_ASCII)))
            RestClient(basicHttpAuth, config.hourRequestLimit)
        } else {
            RestClient(null, config.hourRequestLimit)
        }
    }

    /**
     * Checks if credentials provided in config object are valid
     * @return true if user is authorized
     */
    fun isAuthorized(): Boolean {
        if (config.anonymous) {
            return true
        }

        try {
            getPost(2079472) //perform simple request
        } catch(e: HttpStatusException) {
            if (intArrayOf(401, 403, 404).contains(e.statusCode)) return false
            throw e
        }

        return true
    }

    /**
     * Query API and get [Tag] resource
     * @name tag name, as seen on Danbooru
     */
    fun getTag(name: String): Tag {
        val searchResult = restClient.get(arrayOf(URL, "tags.json", "?search[name_matches]=$name")).array
        if (searchResult.size() == 0) throw IllegalStateException("Query did not match any tag: $name")
        if (searchResult.size() > 1) throw IllegalStateException("Query matched more than one tag")
        return Tag(
                searchResult.first(),
                restClient.get(arrayOf(URL, "tag_aliases.json", "?search[name_matches]=$name")),
                restClient.get(arrayOf(URL, "tag_implications.json", "?search[antecedent_name]=$name")))
    }

    /** Query API and get [Post] resource */
    fun getPost(url: String): Post {
        return getPost(postIdFromUrl(url))
    }

    /** Query API and get [Post] resource */
    fun getPost(id: Int): Post {
        return Post(restClient.get(arrayOf(URL, "posts/$id.json")))
    }

    /**
     * Extracts post id from Danbooru URL
     * @param url from which post id will be extracted
     * @return extracted post id
     */
    fun postIdFromUrl(url: String): Int {
        val beginIndex = url.indexOf(POSTS_URL) + POSTS_URL.length
        val endIndex = url.indexOf('/', beginIndex)
        return url.substring(beginIndex, if (endIndex == -1) url.length else endIndex).toInt()
    }

    private fun fetchNotesPage(id: Int, page: Int): List<JsonElement> {
        val results = ArrayList<JsonElement>()
        val json = restClient.get(arrayOf(URL, "notes.json", "?group_by=note&search[post_id]=$id&page=$page")).array
        json.forEach { results.add(it) }
        return results
    }


    /** Query API and get [Note] resources. [IterablePagedResource] is used for seamless page fetching. */
    fun getPostNotes(id: Int): IterablePagedResource<Note> = IterablePagedResource({ fetchNotesPage(id, it) }, { it -> Note(it) })

    /** Post model. */
    class Post(val json: JsonElement) {
        val id by lazy { json["id"].int }
        val artistTags by lazy { getTags("tag_string_artist") }
        val generalTags by lazy { getTags("tag_string_general") }
        val characterTags by lazy { getTags("tag_string_character") }
        val copyrightTags by lazy { getTags("tag_string_copyright") }
        val tags by lazy { getTags("tag_string") }
        val rating by lazy { Rating.fromDanbooruId(json["rating"].string) }
        val width by lazy { json["image_width"].int }
        val height by lazy { json["image_height"].int }
        val hasNotes by lazy { !json["last_noted_at"].isJsonNull }

        fun getUrl(): String {
            return URL + "posts/$id"
        }

        private fun getTags(elementName: String): List<String> {
            return json[elementName].string.split(" ")
        }
    }

    /** Tag model. */
    class Tag(val json: JsonElement, val aliasesJson: JsonElement, val implicationsJson: JsonElement) {
        val name by lazy { json["name"].string }
        val category by lazy { TagCategory.fromDanbooruId(json["category"].int) }

        val relatedTags by lazy {
            json["related_tags"].string.split(" ")
                    .drop(2) //first two elements are *this* tag and '1'
                    .filterIndexed { index, _ -> index % 2 == 0 } //after each tag numeric value is stored which is not needed
        }

        val aliases by lazy { aliasesJson.array.map { it["antecedent_name"].string } }
        val implications by lazy { implicationsJson.array.map { it["consequent_name"].string } }
    }

    /** Note model. */
    class Note(val json: JsonElement) {
        val x by lazy { json["x"].int }
        val y by lazy { json["y"].int }
        val width by lazy { json["width"].int }
        val height by lazy { json["height"].int }
        val body by lazy { json["body"].string.replace("\n", "<br>") }
        val active by lazy { json["is_active"].bool }
    }

    /** Possible Danbooru tag categories. */
    enum class TagCategory(val danbooruId: Int, val remapName: String) {
        General(0, "general"),
        Artist(1, "artist"),
        Copyright(3, "copyright"),
        Character(4, "character"),
        Meta(5, "meta");

        companion object {
            fun fromDanbooruId(danbooruId: Int): TagCategory {
                return values().first { it.danbooruId == danbooruId }
            }
        }

        override fun toString(): String {
            return this.remapName
        }
    }

    /** Possible Danbooru ratings. */
    enum class Rating(val danbooruId: String) {
        Explicit("e"),
        Questionable("q"),
        Safe("s");

        fun toSzurubooruSafety(): Szurubooru.Safety {
            when (this) {
                Explicit -> return Szurubooru.Safety.Unsafe
                Questionable -> return Szurubooru.Safety.Sketchy
                Safe -> return Szurubooru.Safety.Safe
            }
        }

        companion object {
            fun fromDanbooruId(danbooruId: String): Rating {
                return values().first { it.danbooruId == danbooruId }
            }
        }
    }
}
