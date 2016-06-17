package com.kotcrab.szurubooru.tagger

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
    }

    fun synchronizeTags() {
        val createdTags = ArrayList<EscapedTag>()
        val newPosts = szurubooru.listAllPosts(config.triggerTag)
        log("There are ${newPosts.size} posts that needs to be tagged")

//      val managedPosts = szurubooru.listAllPosts(config.managedTag)
//      log("There are ${managedPosts.size} posts that are managed by tagger")

        newPosts.forEachIndexed { i, post ->
//            if (i > 3) return@forEachIndexed //TODO DEBUG ONLY

            if (post.isImage() == false) {
                logErr("Post ${post.id} is not an image.")
                replacePostTriggerTag(post, config.errorTag)
                return@forEachIndexed
            }

            val sourceImageUrl = searchPostOnIqdb(post)
            sourceImageUrl ?: return@forEachIndexed

            val danPost = danbooru.getPost(sourceImageUrl)
            if (config.updateImageRating) {
                szurubooru.updatePostSafety(post.id, danPost.rating.toSzurubooruSafety())
                log("Updated post ${post.id} safety to ${danPost.rating}")
            }

            //TODO: Add regex name check
            val newPostTags = toSzuruTags(danPost.tags)
            newPostTags.forEach {
                val escapedTag = szurubooru.escapeTagName(it)
                if (szuruTags.contains(escapedTag) == false) createdTags.add(EscapedTag(it, escapedTag))
            }

            szurubooru.updatePostTags(post.id, *escapeTags(newPostTags).plus(config.managedTag).toTypedArray())
            log("Updated post ${post.id} tags. Completed ${i + 1}/${newPosts.size}.")

            Thread.sleep(500)
        }

        log("There are ${createdTags.size} new tags that needs to be updated")
        createdTags.forEachIndexed { i, tag ->
            val danTag = danbooru.getTag(tag.danbooruTag)
            szurubooru.updateTag(tag.escapedTag, remapTagCategroy(danTag.category.remapName),
                    if (config.tags.obtainAliases) toEscapedSzuruTags(danTag.aliases) else emptyList<String>(),
                    if (config.tags.obtainImplications) toEscapedSzuruTags(danTag.implications) else emptyList<String>(),
                    if (config.tags.obtainSuggestions) toEscapedSzuruTags(danTag.relatedTags) else emptyList<String>())
            log("Updated tag ${tag.danbooruTag}. Completed ${i + 1}/${createdTags.size}.")
        }
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

    private fun toSzuruTags(tags: List<String>): List<String> {
        return tags
                .filterNot({ config.tags.ignoreTags.contains(it) })
                .map { remapTag(it) }
    }

    private fun toEscapedSzuruTags(tags: List<String>): List<String> {
        return escapeTags(toSzuruTags(tags))
    }

    private fun escapeTags(tags: List<String>): List<String> = tags.map { szurubooru.escapeTagName(it) }

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
}
