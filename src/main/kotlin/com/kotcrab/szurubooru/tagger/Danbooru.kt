package com.kotcrab.szurubooru.tagger

import com.github.salomonbrys.kotson.get
import com.github.salomonbrys.kotson.int
import com.github.salomonbrys.kotson.string
import com.google.gson.JsonElement
import com.google.gson.JsonParser
import org.apache.commons.codec.binary.Base64
import org.jsoup.Jsoup

/**
 * Simple Danbooru client, only method required for auto-tagger are implemented.
 * @author Kotcrab
 */
class Danbooru(private val config: DanbooruDto) {
    companion object {
        val URL = "https://danbooru.donmai.us/"
    }

    val jsonParser = JsonParser();
    val basicHttpAuth: String;

    init {
        if (config.anonymous == false) {
            var login: String = "${config.username}:${config.apiKey}";
            basicHttpAuth = String(Base64.encodeBase64(login.toByteArray(charset = Charsets.US_ASCII)));
        } else {
            basicHttpAuth = "";
        }
    }

    fun getTag(name: String): Tag {
        val searchResult = request("tags.json?search[name_matches]=$name").asJsonArray
        if (searchResult.size() == 0) throw IllegalStateException("Query did not match any tag");
        if (searchResult.size() > 1) throw IllegalStateException("Query matched more than one tag")
        return Tag(
                searchResult.first(),
                request("tag_aliases.json?search[name_matches]=$name"),
                request("tag_implications.json?search[antecedent_name]=$name"))
    }

    fun getPost(id: Int): Post {
        return Post(request("posts/$id.json"));
    }

    private fun request(requestUrl: String): JsonElement {
        val request = Jsoup.connect("$URL$requestUrl").ignoreContentType(true)
        if (config.anonymous == false)
            request.header("Authorization", "Basic " + basicHttpAuth)
        val json = request.execute().body();
        return jsonParser.parse(json);
    }

    class Post(val json: JsonElement) {
        val artistTags by lazy { getTags("tag_string_artist") }
        val generalTags by lazy { getTags("tag_string_general") }
        val characterTags  by lazy { getTags("tag_string_character") }
        val copyrightTags  by lazy { getTags("tag_string_copyright") }
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
                    .filterIndexed { index, s -> index % 2 == 0 }; //after each tag numeric value is stored which is not needed  }
        }

        val aliases by lazy { aliasesJson.asJsonArray.map { it["antecedent_name"].string } }
        val implications by lazy { implicationsJson.asJsonArray.map { it["consequent_name"].string } }
    }

    enum class TagCategory(val danbooruId: Int) {
        General(0),
        Artist(1),
        Copyright(3),
        Character(4);

        companion object {
            fun fromDanbooruId(danbooruId: Int): TagCategory {
                return values().first { it.danbooruId == danbooruId }
            }
        }
    }

    enum class Rating(val danbooruId: String) {
        Explicit("e"),
        Questionable("q"),
        Safe("s");

        companion object {
            fun fromDanbooruId(danbooruId: String): Rating {
                return values().first { it.danbooruId == danbooruId }
            }
        }
    }
}
