package com.kotcrab.szurubooru.tagger

import com.github.salomonbrys.kotson.jsonArray
import com.github.salomonbrys.kotson.jsonObject
import com.google.gson.JsonObject
import java.io.IOException
import java.net.BindException
import java.net.InetAddress
import java.net.ServerSocket
import java.util.*
import kotlin.system.exitProcess

/** @author Kotcrab */
class AutoTagger(private val config: ConfigDto) {
    private lateinit var lockSocket: ServerSocket

    val danbooru = Danbooru(config.danbooru)
    val szurubooru = Szurubooru(config.szurubooru)

    lateinit var szuruTags: List<String>
    lateinit var szuruTagCategories: List<String>
    lateinit var tagNameRegex: Regex

    init {
        if (config.singleInstance.enabled) {
            checkIfRunning()
        }

        if (config.checkBooruConnectivity) {
            if (szurubooru.isHostReachable() == false) throw IllegalStateException("Can't to connect to Szurubooru using API URL: ${config.szurubooru.apiPath}")
            if (szurubooru.isAuthorized() == false) throw IllegalStateException("Failed to authorize to Szurubooru using provided credentials: ${config.szurubooru.username}")
            if (danbooru.isAuthorized() == false) throw IllegalStateException("Failed to authorize to Danbooru using provided credentials: ${config.danbooru.username}")
            log("Booru connectivity looks ok")
        } else {
            log("Booru connectivity check skipped")
        }

        log("Obtaining tags.json...")
        szuruTags = szurubooru.getTags()
        szuruTagCategories = szurubooru.getTagCategories()
        tagNameRegex = Regex(szurubooru.getInfo().tagNameRegex)

        val missingCategories = Danbooru.TagCategory.values().filterNot { szuruTagCategories.contains(remapTagCategroy(it.remapName)) }
        if (missingCategories.size != 0) throw IllegalStateException("Szurubooru is missing required tag categories: ${missingCategories.joinToString { it.remapName }}. " +
                "Make sure they exist or modify tag category remapping configuration.")
    }

    fun synchronizeTags() {
        val newPosts = szurubooru.listAllPosts(config.triggerTag)
        log("There are ${newPosts.size} posts that needs to be tagged")

//      val managedPosts = szurubooru.listAllPosts(config.managedTag)
//      log("There are ${managedPosts.size} posts that are managed by tagger")

        val createdTags = ArrayList<EscapedTag>()
        val postsToBeNoted = ArrayList<PostSet>()
        updatePostsTags(newPosts, postsToBeNoted, createdTags)
        updatePostsNotes(postsToBeNoted)
        updateTags(createdTags)
    }

    private fun updatePostsTags(posts: List<Szurubooru.Post>, postsToBeNoted: ArrayList<PostSet>, createdTags: ArrayList<EscapedTag>) {
        posts.forEachIndexed { i, post ->
            try {
                updatePostTags(post, postsToBeNoted, createdTags)
                log("Updated post ${post.id} tags. Completed ${i + 1}/${posts.size}.")
            } catch(e: Exception) {
                logErr("Error occurred while updating post ${post.id} tags")
                e.printStackTrace()

                try {
                    replacePostTriggerTag(post, config.errorTag)
                } catch(e: Exception) {
                    logErr("Additional error occurred when tried to append error tag to post")
                    e.printStackTrace()
                }
            }
        }
    }

    private fun updatePostTags(post: Szurubooru.Post, postsToBeNoted: ArrayList<PostSet>, createdTags: ArrayList<EscapedTag>) {
        if (post.isImage() == false) {
            logErr("Post ${post.id} is not an image.")
            replacePostTriggerTag(post, config.errorTag)
            return
        }

        val sourceImageUrl = searchPostOnIqdb(post)
        sourceImageUrl ?: return

        val danPost = danbooru.getPost(sourceImageUrl)
        if (config.updateImageRating) {
            szurubooru.updatePostSafety(post.id, danPost.rating.toSzurubooruSafety())
            log("Updated post ${post.id} safety to ${danPost.rating}")
        }
        if (config.updateImageNotes && danPost.hasNotes) {
            postsToBeNoted.add(PostSet(post, danPost))
        }

        val newPostTags = toSzuruTags(danPost.tags)
        newPostTags.forEach {
            val escapedTag = szurubooru.escapeTagName(it)
            if (tagNameRegex.matches(escapedTag) == false) return@forEach
            if (szuruTags.contains(escapedTag) == false) createdTags.add(EscapedTag(it, escapedTag))
        }

        szurubooru.updatePostTags(post.id, *escapeTags(newPostTags).plus(config.managedTag).toTypedArray())
    }

    private fun searchPostOnIqdb(post: Szurubooru.Post): String? {
        log("Searching IQDB match for post ${post.id}...")
        val sourceImageUrl = szurubooru.searchPostOnIqdb(post)

        if (sourceImageUrl == null) {
            log("Post ${post.id} not found in the IQDB datebase.")
            replacePostTriggerTag(post, config.noMatchTag)
        } else {
            log("Found post ${post.id} match: $sourceImageUrl")
            if (config.storeSourceUrl) {
                szurubooru.updatePostSource(post.id, sourceImageUrl)
                log("Updated post ${post.id} source")
            }
        }

        return sourceImageUrl
    }

    private fun updatePostsNotes(postsToBeNoted: ArrayList<PostSet>) {
        if (config.updateImageNotes) {
            log("There are ${postsToBeNoted.size} posts that will have notes updated.")
            postsToBeNoted.forEachIndexed { i, postSet ->
                try {
                    updatePostNote(postSet)
                    log("Updated post ${postSet.szuruPost.id} notes. Completed ${i + 1}/${postsToBeNoted.size}.")
                } catch(e: Exception) {
                    logErr("Error occurred while updating post ${postSet.szuruPost.id} notes")
                    e.printStackTrace()
                }
            }
        }
    }

    private fun updatePostNote(postSet: PostSet) {
        val danPost = postSet.danPost
        val szuruPost = postSet.szuruPost
        val danNotes = danbooru.getPostNotes(postSet.danPost.id)
        val szuruNotes = ArrayList<JsonObject>()
        danNotes.forEach { note ->
            if (note.active == false) return@forEach

            val noteX = note.x.toFloat() / danPost.width
            val noteY = note.y.toFloat() / danPost.height
            val noteWidth = note.width.toFloat() / danPost.width
            val noteHeight = note.height.toFloat() / danPost.height
            val note = jsonObject(
                    "polygon" to jsonArray(
                            jsonArray(noteX, noteY),
                            jsonArray(noteX + noteWidth, noteY),
                            jsonArray(noteX + noteWidth, noteY + noteHeight),
                            jsonArray(noteX, noteY + noteHeight)
                    ),
                    "text" to note.body
            )
            szuruNotes.add(note)
        }
        szurubooru.updatePostNotes(szuruPost.id, szuruNotes)

    }

    private fun updateTags(createdTags: ArrayList<EscapedTag>) {
        log("There are ${createdTags.size} new tags that needs to be updated")
        createdTags.forEachIndexed { i, tag ->
            try {
                updateTag(tag)
                log("Updated tag ${tag.danbooruTag}. Completed ${i + 1}/${createdTags.size}.")
            } catch(e: Exception) {
                logErr("Error occurred while updating tag ${tag.danbooruTag}")
                e.printStackTrace()
            }
        }
    }

    private fun updateTag(tag: EscapedTag) {
        val danTag = danbooru.getTag(tag.danbooruTag)
        szurubooru.updateTag(tag.escapedTag, remapTagCategroy(danTag.category.remapName),
                if (config.tags.obtainAliases) toEscapedSzuruTags(danTag.aliases) else emptyList<String>(),
                if (config.tags.obtainImplications) toEscapedSzuruTags(danTag.implications) else emptyList<String>(),
                if (config.tags.obtainSuggestions) toEscapedSzuruTags(danTag.relatedTags) else emptyList<String>())
    }

    private fun toSzuruTags(tags: List<String>): List<String> {
        return tags
                .filterNot { config.tags.ignoreTags.contains(it) }
                .filterNot { it.startsWith("/") } //all Danbooru tags that starts with / seems to be shortcuts for other tags
                .map { remapTag(it) }
    }

    private fun toEscapedSzuruTags(tags: List<String>): List<String> {
        return escapeTags(toSzuruTags(tags))
    }

    private fun escapeTags(tags: List<String>): List<String> {
        return tags
                .map { szurubooru.escapeTagName(it) }
                .filter {
                    val matches = tagNameRegex.matches(it)
                    if (matches == false) log("Removing invalid tag $it (did not match server tag name regex)")
                    matches
                }
    }

    private fun remapTag(tag: String): String {
        config.tags.remapTags.forEach { remap: RemapDto ->
            if (remap.from.equals(tag)) return remap.to
        }

        return tag
    }

    private fun remapTagCategroy(category: String): String {
        config.tags.remapCategories.forEach { remap: RemapDto ->
            if (remap.from.equals(category)) return remap.to
        }

        return category
    }

    private fun replacePostTriggerTag(post: Szurubooru.Post, newTag: String) {
        val newTagsList = post.tags.minus(config.triggerTag).plus(newTag)
        szurubooru.updatePostTags(post.id, *newTagsList.toTypedArray())
    }

    fun dispose() {
        if (config.singleInstance.enabled) {
            lockSocket.close()
        }
    }

    private fun checkIfRunning() {
        try {
            lockSocket = ServerSocket(config.singleInstance.port, 0, InetAddress.getByAddress(byteArrayOf(127, 0, 0, 1)))
        } catch (e: BindException) {
            logErr("Another instance is already running or port ${config.singleInstance.port} is being used by other application.")
            exitProcess(1)
        } catch (e: IOException) {
            e.printStackTrace()
            exitProcess(2)
        }
    }

    private class EscapedTag(val danbooruTag: String, val escapedTag: String)

    private class PostSet(val szuruPost: Szurubooru.Post, val danPost: Danbooru.Post)
}
