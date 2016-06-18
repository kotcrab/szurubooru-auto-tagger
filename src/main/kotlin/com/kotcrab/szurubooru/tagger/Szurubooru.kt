package com.kotcrab.szurubooru.tagger

import com.github.salomonbrys.kotson.*
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import org.apache.commons.codec.binary.Base64
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
    private val restClient by lazy {
        val login: String = "${config.username}:${config.password}"
        val basicHttpAuth = String(Base64.encodeBase64(login.toByteArray(charset = Charsets.US_ASCII)))
        RestClient(basicHttpAuth)
    }

    init {
        if (config.apiPath.endsWith("/") == false) config.apiPath += "/"
        if (config.dataPath.endsWith("/") == false) config.dataPath += "/"
    }

    fun isHostReachable(): Boolean {
        try {
            restClient.get(arrayOf(config.apiPath, "posts"))
            return true
        } catch(e: IOException) {
            return false
        }
    }

    fun isAuthorized(): Boolean {
        try {
            restClient.get(arrayOf(config.apiPath, "posts", "?bump-login"))
            return true
        } catch(e: HttpStatusException) {
            if (intArrayOf(401, 403, 404).contains(e.statusCode)) return false
            throw e
        }
    }

    fun getTagCategories(): List<String> {
        return restClient.get(arrayOf(config.apiPath, "tag-categories", "?fields=name"))["results"].array.map { it["name"].string }
    }

    fun getTags(): List<String> {
        val json = restClient.get(arrayOf(config.dataPath, "tags.json"))
        val list = json["tags"].array.flatMap {
            it["names"].array.map { it.string }
        }
        return list
    }

    fun getInfo(): Info {
        return Info(restClient.get(arrayOf(config.apiPath, "info")))
    }

    fun escapeTagName(name: String): String {
        var escapedName = name
        arrayOf('[', ']', '{', '}', '/', '\\', ' ', '<', '>', '=', '+', ';', '+', '@', '|', '!', '?')
                .forEach { escapedName = escapedName.replace(it, '_') }
        escapedName = escapedName.substring(0, 1).plus(escapedName.substring(1).replace(':', '_')) //: is supported as first character
        return escapedName
    }

    fun uploadFile(file: File, safety: Safety, vararg tags: String) {
        if (file.exists() == false) throw IllegalStateException("file does not exist")

        val json = jsonObject(
                "safety" to safety.szurubooruName,
                "tags" to jsonArray(*tags)
        ).toString()

        restClient.post(arrayOf(config.apiPath, "posts/"),
                arrayOf(StringPostArg("metadata", json),
                        FilePostArg("content", file.name, FileInputStream(file))))
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
        val json = jsonObject("safety" to safety.szurubooruName)
        updatePostData(id, json)
    }

    fun updatePostTags(id: Int, vararg tags: String) {
        val json = jsonObject("tags" to jsonArray(*tags))
        updatePostData(id, json)
    }

    fun updatePostSource(id: Int, source: String) {
        val json = jsonObject("source" to source)
        updatePostData(id, json)
    }

    fun updatePostNotes(id: Int, notes: List<JsonObject>) {
        val json = jsonObject("notes" to jsonArray(*notes.toTypedArray()))
        updatePostData(id, json)
    }

    private fun updatePostData(id: Int, json: JsonObject) {
        restClient.put(arrayOf(config.apiPath, "post/$id"), json)
    }

    fun updateTag(name: String, category: String, aliases: List<String>, implications: List<String>, suggestions: List<String>) {
        val json = jsonObject(
                "names" to jsonArray(name, *aliases.minus(name).toTypedArray()),
                "category" to category
        )

        if (implications.size != 0) json += "implications" to jsonArray(*implications.minus(name).toTypedArray())
        if (suggestions.size != 0) json += "suggestions" to jsonArray(*suggestions.minus(name).toTypedArray())

        restClient.put(arrayOf(config.apiPath, "tag/$name"), json)
    }

    fun listAllPosts(query: String): List<Post> {
        var page = 1
        val posts = ArrayList<Post>()

        while (true) {
            val json = restClient.get(arrayOf(config.apiPath, "posts/", "?page=$page&pageSize=100&query=$query"))
            val postsJson = json["results"].array
            if (postsJson.size() == 0) break
            postsJson.forEach { posts.add(Post(it)) }
            page++
        }

        return posts
    }

    fun getPost(id: Int): Post {
        return Post(restClient.get(arrayOf(config.apiPath, "post/$id")))
    }

    class Post(val json: JsonElement) {
        val id by lazy { json["id"].int }
        val contentUrl by lazy { json["contentUrl"].string }
        val tags by lazy { json["tags"].array.map { it.string } }
        val safety by lazy { Safety.fromSzurubooruId(json["safety"].string) }

        fun isImage(): Boolean {
            return json["type"].string == "image"
        }
    }

    class Info(val json: JsonElement) {
        val tagNameRegex by lazy { json["config"]["tagNameRegex"].string }
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
