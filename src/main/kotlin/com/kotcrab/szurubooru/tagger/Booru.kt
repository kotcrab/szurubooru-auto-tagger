package com.kotcrab.szurubooru.tagger

import com.github.salomonbrys.kotson.get
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

    fun getPost(id: Int): DanbooruPost {
        val request = Jsoup.connect("${URL}posts/$id.json").ignoreContentType(true)
        if (config.anonymous == false)
            request.header("Authorization", "Basic " + basicHttpAuth)
        val json = request.execute().body();
        return DanbooruPost(jsonParser.parse(json));
    }
}

class DanbooruPost(val json: JsonElement) {
    fun getArtistTags(): List<String> {
        return getTags("tag_string_artist")
    }

    fun getGeneralTags(): List<String> {
        return getTags("tag_string_general")
    }

    fun getCharacterTags(): List<String> {
        return getTags("tag_string_character")
    }

    fun getCopyrightTags(): List<String> {
        return getTags("tag_string_copyright")
    }

    private fun getTags(elementName: String): List<String> {
        return json[elementName].asString.split(" ")
    }
}

