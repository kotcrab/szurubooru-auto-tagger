package com.kotcrab.szurubooru.tagger

import com.github.salomonbrys.kotson.*
import com.google.gson.JsonElement
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
    private val jsonParser = JsonParser()
    private val basicHttpAuth: String

    init {
        val login: String = "${config.username}:${config.password}"
        basicHttpAuth = String(Base64.encodeBase64(login.toByteArray(charset = Charsets.US_ASCII)))
    }

    fun isHostReachable(): Boolean {
        try {
            request("posts")
            return true
        } catch(e: IOException) {
            return false
        }
    }

    fun isAuthorized(): Boolean {
        try {
            request("posts?bump-login")
            return true
        } catch(e: HttpStatusException) {
            if (intArrayOf(401, 403, 404).contains(e.statusCode)) return false
            throw e
        }
    }

    fun getTagCategories(): List<String> {
        return request("tag-categories?fields=name")["results"].array.map { it["name"].string }
    }

    fun getTags(): List<String> {
        val json = Jsoup.connect("${config.dataPath}tags.json").validateTLSCertificates(false).ignoreContentType(true).execute().body();
        val list = jsonParser.parse(json)["tags"].asJsonArray.flatMap {
            it["names"].asJsonArray.map { it.asString }
        }
        return list
    }
    fun uploadFile(file: File, safety: Safety, vararg tags: String) {
        if (file.exists() == false) throw IllegalStateException("file does not exist")

        val json = jsonObject(
                "safety" to safety.szurubooruName,
                "tags" to jsonArray(*tags)
        ).toString()

        prepareRequest("posts/").timeout(10 * 1000)
                .data("metadata", json)
                .data("content", file.name, FileInputStream(file))
                .post()
    }

    fun searchPostOnIqdb(post: Post): String? {
        if (post.isImage() == false) throw IllegalArgumentException("Post must be an image")
        val imageFile = createTempFile()
        imageFile.writeBytes(Jsoup.connect(post.contentUrl).timeout(30 * 1000)
                .ignoreContentType(true).execute().bodyAsBytes())
        val sourceImageUrl = queryIqdb(imageFile)
        imageFile.delete()
        return sourceImageUrl
    }

    fun updatePostSafety(id: Int, safety: Safety) {
        val json = jsonObject(
                "safety" to safety.szurubooruName
        ).toString()
        updatePostData(id, json)
    }

    fun updatePostTags(id: Int, vararg tags: String) {
        val json = jsonObject(
                "tags" to jsonArray(*tags)
        ).toString()
        updatePostData(id, json)
    }

    fun updatePostSource(id: Int, source: String) {
        val json = jsonObject(
                "source" to source
        ).toString()
        updatePostData(id, json)
    }

    private fun updatePostData(id: Int, json: String) {
        prepareRequest("post/$id").method(Connection.Method.PUT)
                .requestBody(json).execute()
    }

    fun listAllPosts(query: String): List<Post> {
        var page = 1
        val posts = ArrayList<Post>()

        while (true) {
            val json = request("posts/?page=$page&pageSize=100&query=$query")
            val postsJson = json["results"].asJsonArray
            if (postsJson.size() == 0) break
            postsJson.forEach { posts.add(Post(it)) }
            page++
        }

        return posts
    }

    private fun prepareRequest(requestUrl: String): Connection {
        val request = Jsoup.connect("${config.apiPath}$requestUrl").validateTLSCertificates(false).ignoreContentType(true)
        request.header("Authorization", "Basic $basicHttpAuth")
        return request
    }

    private fun request(requestUrl: String): JsonElement {
        return jsonParser.parse(prepareRequest(requestUrl).execute().body())
    }

    class Post(val json: JsonElement) {
        val id by lazy { json["id"].asInt }
        val contentUrl by lazy { json["contentUrl"].asString }
        val tags by lazy { json["tags"].asJsonArray.map { it.asString } }
        val safety by lazy { Safety.fromSzurubooruId(json["safety"].asString) }

        fun isImage(): Boolean {
            return json["type"].asString == "image"
        }
    }

    enum class Safety(val szurubooruName: String) {
        Unsafe("unsafe"),
        Sketchy("sketchy"),
        Safe("safe");

        companion object {
            fun fromSzurubooruId(szurubooruName: String): Safety {
                return Safety.values().first { it.szurubooruName == szurubooruName }
            }
        }
    }
}
