package com.kotcrab.szurubooru.tagger

import com.github.salomonbrys.kotson.array
import com.github.salomonbrys.kotson.get
import com.github.salomonbrys.kotson.int
import com.github.salomonbrys.kotson.string
import com.google.gson.JsonElement
import org.apache.commons.codec.binary.Base64
import org.jsoup.HttpStatusException

/**
 * Simple Danbooru client, only method required for auto-tagger are implemented.
 * @author Kotcrab
 */
class Danbooru(private val config: DanbooruDto) {
    companion object {
        val URL = "https://danbooru.donmai.us/"
    }

    private val restClient by lazy {
        if (config.anonymous == false) {
            val login: String = "${config.username}:${config.apiKey}"
            val basicHttpAuth = String(Base64.encodeBase64(login.toByteArray(charset = Charsets.US_ASCII)))
            RestClient(basicHttpAuth)
        } else {
            RestClient(null)
        }
    }

    var requestCounter = 0
        private set

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

    fun postIdFromUrl(url: String): Int {
        val searchString = "danbooru.donmai.us/posts/"
        val beginIndex = url.indexOf(searchString) + searchString.length
        val endIndex = url.indexOf('/', beginIndex)
        return url.substring(beginIndex, if (endIndex == -1) url.length else endIndex).toInt()
    }

    fun getTag(name: String): Tag {
        val searchResult = restClient.get(arrayOf(URL, "tags.json", "?search[name_matches]=$name")).array
        if (searchResult.size() == 0) throw IllegalStateException("Query did not match any tag: $name")
        if (searchResult.size() > 1) throw IllegalStateException("Query matched more than one tag")
        return Tag(
                searchResult.first(),
                restClient.get(arrayOf(URL, "tag_aliases.json", "?search[name_matches]=$name")),
                restClient.get(arrayOf(URL, "tag_implications.json", "?search[antecedent_name]=$name")))
    }

    fun getPost(url: String): Post {
        return getPost(postIdFromUrl(url))
    }

    fun getPost(id: Int): Post {
        return Post(restClient.get(arrayOf(URL, "posts/$id.json")))
    }

    class Post(val json: JsonElement) {
        val artistTags by lazy { getTags("tag_string_artist") }
        val generalTags by lazy { getTags("tag_string_general") }
        val characterTags by lazy { getTags("tag_string_character") }
        val copyrightTags by lazy { getTags("tag_string_copyright") }
        val tags by lazy { getTags("tag_string") }
        val rating by lazy { Rating.fromDanbooruId(json["rating"].string) }

        private fun getTags(elementName: String): List<String> {
            return json[elementName].string.split(" ")
        }
    }

    class Tag(val json: JsonElement, val aliasesJson: JsonElement, val implicationsJson: JsonElement) {
        val name by lazy { json["name"].string }
        val category by lazy { TagCategory.fromDanbooruId(json["category"].int) }

        val relatedTags by lazy {
            json["related_tags"].string.split(" ")
                    .drop(2) //first two elements are *this* tag and '1'
                    .filterIndexed { index, s -> index % 2 == 0 } //after each tag numeric value is stored which is not needed
        }

        val aliases by lazy { aliasesJson.array.map { it["antecedent_name"].string } }
        val implications by lazy { implicationsJson.array.map { it["consequent_name"].string } }
    }

    enum class TagCategory(val danbooruId: Int, val remapName: String) {
        General(0, "general"),
        Artist(1, "artist"),
        Copyright(3, "copyright"),
        Character(4, "character");

        companion object {
            fun fromDanbooruId(danbooruId: Int): TagCategory {
                return values().first { it.danbooruId == danbooruId }
            }
        }

        override fun toString(): String {
            return this.remapName
        }
    }

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
