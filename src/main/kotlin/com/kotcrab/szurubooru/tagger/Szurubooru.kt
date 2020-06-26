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
        val json = restClient.get(arrayOf(config.apiPath, "tags"))
        val list = json["results"].array.flatMap {
            it["names"].array.map { it.string }
        }
        return list
    }

    fun getTag(name: String): Tag {
        return Tag(restClient.get(arrayOf(config.apiPath, "tag/$name")))
    }

    fun getInfo(): Info {
        return Info(restClient.get(arrayOf(config.apiPath, "info")))
    }

    fun uploadFile(file: File, safety: Safety, vararg tags: String) {
        if (file.exists() == false) throw IllegalStateException("file does not exist")

        val json = jsonObject(
                "safety" to safety.szurubooruName,
                "tags" to jsonArray(*tags)
        ).toString()

        val inputStream = FileInputStream(file)
        restClient.post(arrayOf(config.apiPath, "posts/"),
                arrayOf(StringPostArg("metadata", json),
                        FilePostArg("content", file.name, inputStream)))
        inputStream.close()
    }

    fun createPostComment(post: Post, text: String) {
        val json = jsonObject(
                "postId" to post.id,
                "text" to text).toString()
        restClient.post(arrayOf(config.apiPath, "comments"), json)
    }

    fun getPost(id: Int): Post {
        return Post(restClient.get(arrayOf(config.apiPath, "post/$id")))
    }

    fun searchPostOnIqdb(post: Post): String? {
        if (post.isImage() == false) throw IllegalArgumentException("Post must be an image")
        val imageFile = createTempFile()
        imageFile.writeBytes(Jsoup.connect(config.dataPath + post.contentUrl).timeout(30 * 1000).maxBodySize(0)
                .ignoreContentType(true).execute().bodyAsBytes())
        val sourceImageUrl = queryIqdb(imageFile)
        imageFile.delete()
        return sourceImageUrl
    }

    fun updatePostSafety(post: Post, safety: Safety) {
        val json = jsonObject("safety" to safety.szurubooruName)
        updatePostData(post, json)
    }

    fun updatePostTags(post: Post, vararg tags: String) {
        val json = jsonObject("tags" to jsonArray(*tags))
        updatePostData(post, json)
    }

    fun updatePostSource(post: Post, source: String) {
        val json = jsonObject("source" to source)
        updatePostData(post, json)
    }

    fun updatePostNotes(post: Post, notes: List<JsonObject>) {
        val json = jsonObject("notes" to jsonArray(*notes.toTypedArray()))
        updatePostData(post, json)
    }

    private fun updatePostData(post: Post, json: JsonObject) {
        json += "version" to post.version
        restClient.put(arrayOf(config.apiPath, "post/${post.id}"), json)
        post.version++
    }

    fun updateTag(name: String, category: String, aliases: List<String>, implications: List<String>, suggestions: List<String>) {
        val version = getTag(name).version
        val json = jsonObject(
                "names" to jsonArray(name, *aliases.minus(name).toTypedArray()),
                "category" to category,
                "version" to version
        )

        if (implications.size != 0) json += "implications" to jsonArray(*implications.minus(name).toTypedArray())
        if (suggestions.size != 0) json += "suggestions" to jsonArray(*suggestions.minus(name).toTypedArray())

        restClient.put(arrayOf(config.apiPath, "tag/$name"), json)
    }


    private fun fetchPage(baseUrl: String, page: Int, query: String): List<JsonElement> {
        val results = ArrayList<JsonElement>()
        val pageSize = 100
        val json = restClient.get(arrayOf(config.apiPath, baseUrl, "?offset=${pageSize * page}&limit=$pageSize&query=$query"))["results"].array
        json.forEach { results.add(it) }
        return results
    }

    fun pagedPosts(query: String): PagedResource<Post> = PagedResource({ fetchPage("posts/", it, query) }, { it -> Post(it) })
    fun pagedTags(query: String): PagedResource<Tag> = PagedResource({ fetchPage("tags/", it, query) }, { it -> Tag(it) })

    fun iterablePosts(query: String): IterablePagedResource<Post> = IterablePagedResource({ fetchPage("posts/", it, query) }, { it -> Post(it) })
    fun iterableTags(query: String): IterablePagedResource<Tag> = IterablePagedResource({ fetchPage("tags/", it, query) }, { it -> Tag(it) })

    class Post(val json: JsonElement) {
        val id by lazy { json["id"].int }
        val contentUrl by lazy { json["contentUrl"].string }
        val mimeType by lazy { json["mimeType"].string }
        val tags by lazy { json["tags"].array.map { it["names"].string } }
        val safety by lazy { Safety.fromSzurubooruId(json["safety"].string) }
        val width by lazy { json["canvasWidth"].int }
        val height by lazy { json["canvasHeight"].int }
        val source by lazy { json["source"].string }
        var version = json["version"].int

        fun isImage(): Boolean {
            return json["type"].string == "image"
        }
    }

    class Tag(val json: JsonElement) {
        val name by lazy { json["names"].array.first().string }
        val wasEdited by lazy { !json["lastEditTime"].isJsonNull }
        val version by lazy { json["version"].int }
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
