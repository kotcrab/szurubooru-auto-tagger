package com.kotcrab.szurubooru.tagger

import com.github.salomonbrys.kotson.array
import com.github.salomonbrys.kotson.get
import com.github.salomonbrys.kotson.string
import com.google.gson.JsonElement
import com.google.gson.JsonParser
import org.apache.commons.codec.binary.Base64
import org.jsoup.HttpStatusException
import org.jsoup.Jsoup
import java.io.IOException

/**
 * Szurubooru client.
 * @author Kotcrab
 */
class Szurubooru(private val config: SzurubooruDto) {
    val jsonParser = JsonParser();
    val basicHttpAuth: String;

    init {
        var login: String = "${config.username}:${config.password}";
        basicHttpAuth = String(Base64.encodeBase64(login.toByteArray(charset = Charsets.US_ASCII)));
    }

    fun isHostReachable(): Boolean {
        try {
            request("posts")
            return true;
        } catch(e: IOException) {
            return false;
        }
    }

    fun isAuthorized(): Boolean {
        try {
            request("posts?bump-login")
            return true;
        } catch(e: HttpStatusException) {
            if (intArrayOf(401, 403, 404).contains(e.statusCode)) return false;
            throw e;
        }
    }

    fun getTagCategories(): List<String> {
        return request("tag-categories?_fields=name")["results"].array.map { it["name"].string }
    }

    private fun request(requestUrl: String): JsonElement {
        val request = Jsoup.connect("${config.apiPath}$requestUrl").validateTLSCertificates(false).ignoreContentType(true)
        request.header("Authorization", "Basic " + basicHttpAuth)
        val json = request.execute().body();
        return jsonParser.parse(json);
    }
}
