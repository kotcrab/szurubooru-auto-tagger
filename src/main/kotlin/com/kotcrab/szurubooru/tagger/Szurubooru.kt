package com.kotcrab.szurubooru.tagger

import com.github.salomonbrys.kotson.*
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.apache.commons.codec.binary.Base64
import org.jsoup.Connection
import org.jsoup.HttpStatusException
import org.jsoup.Jsoup
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.util.*

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

    fun uploadFile(file: File, safety: Safety, vararg tags: String) {
        if (file.exists() == false) throw IllegalStateException("file does not exist")

        val json: JsonObject = jsonObject(
                "safety" to safety.szurubooruName,
                "tags" to jsonArray(*tags)
        )

        prepareRequest("posts/").timeout(10 * 1000)
                .data("metadata", json.toString())
                .data("content", file.name, FileInputStream(File(file.absolutePath)))
                .post()
    }

    fun listAllPosts(query: String): List<Post> {
        var page = 1;
        val posts = ArrayList<Post>()

        while (true) {
            val json = request("posts/?page=$page&pageSize=100&query=$query")
            val postsJson = json["results"].asJsonArray
            if (postsJson.size() == 0) break;
            postsJson.forEach { posts.add(Post(it)) }
            page++
        }

        return posts
    }

    fun prepareRequest(requestUrl: String): Connection {
        val request = Jsoup.connect("${config.apiPath}$requestUrl").validateTLSCertificates(false).ignoreContentType(true)
        request.header("Authorization", "Basic $basicHttpAuth")
        return request;
    }

    fun request(requestUrl: String): JsonElement {
        return jsonParser.parse(prepareRequest(requestUrl).execute().body());
    }

    class Post(val json: JsonElement) {

    }

    enum class Safety(val szurubooruName: String) {
        Unsafe("unsafe"),
        Sketchy("sketchy"),
        Safe("safe");
    }
}
