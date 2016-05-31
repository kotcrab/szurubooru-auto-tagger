package com.kotcrab.szurubooru.tagger

import org.apache.commons.codec.binary.Base64
import org.jsoup.Jsoup


/** @author Kotcrab */
class Danbooru(private val config: DanbooruDto) {
    companion object {
        val URL = "https://danbooru.donmai.us/"
    }

    val basicHttpAuth: String;

    init {
        if (config.anonymous == false) {
            var login: String = "${config.username}:${config.apiKey}";
            basicHttpAuth = String(Base64.encodeBase64(login.toByteArray(charset = Charsets.US_ASCII)));
        } else {
            basicHttpAuth = "";
        }
    }

    fun getPost(id: Int): String {
        val request = Jsoup.connect("${URL}posts/$id.json").ignoreContentType(true)
        if (config.anonymous == false)
            request.header("Authorization", "Basic " + basicHttpAuth)
        val json = request.execute().body();
        return json;
    }
}
