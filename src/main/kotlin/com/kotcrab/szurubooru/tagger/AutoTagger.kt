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
    }

    fun synchronizeTags() {
        val newTags = ArrayList<String>();
        val newPosts = szurubooru.listAllPosts(config.triggerTag)
        log("There are ${newPosts.size} posts that needs to be tagged")

//      val managedPosts = szurubooru.listAllPosts(config.managedTag)
//      log("There are ${managedPosts.size} posts that are managed by tagger")

        newPosts.forEachIndexed { i, post ->
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
            val newPostTags = danPost.tags
                    .filterNot({ config.tags.ignoreTags.contains(it) })
                    .map { remapTag(it) }
                    .map { szurubooru.esacpeTagName(it) }

            newPostTags.forEach { if (szuruTags.contains(it) == false) newTags.add(it) }

            szurubooru.updatePostTags(post.id, *newPostTags.plus(config.managedTag).toTypedArray())
            log("Updated post ${post.id} tags. Completed $i/${newPosts.size}.")

            Thread.sleep(500)
        }

        log("There are ${newTags.size} new tags that needs to be updated")
        newTags.forEach {

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

    fun remapTag(tag: String): String {
        config.tags.remapTags.forEach { remap: RemapDto ->
            if (remap.from.equals(tag)) return remap.to;
        }

        return tag
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
}
