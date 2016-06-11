package com.kotcrab.szurubooru.tagger

import org.jsoup.Jsoup
import java.io.IOException
import java.net.BindException
import java.net.InetAddress
import java.net.ServerSocket
import kotlin.system.exitProcess

/** @author Kotcrab */
class AutoTagger(private val config: ConfigDto) {
    private lateinit var lockSocket: ServerSocket

    val danbooru = Danbooru(config.danbooru)
    val szurubooru = Szurubooru(config.szurubooru)

    init {
        if (config.singleInstance.enabled) {
            checkIfRunning();
        }

        if (config.checkBooruConnectivity) {
            if (szurubooru.isHostReachable() == false) throw IllegalStateException("Can't to connect to Szurubooru using API URL: ${config.szurubooru.apiPath}")
            if (szurubooru.isAuthorized() == false) throw IllegalStateException("Failed to authorize to Szurubooru using provided credentials: ${config.szurubooru.username}")
            if (danbooru.isAuthorized() == false) throw IllegalStateException("Failed to authorize to Danbooru using provided credentials: ${config.danbooru.username}")
            log("Booru connectivity looks ok")
        } else {
            log("Booru connectivity check skipped")
        }
    }

    fun synchronizeTags() {
        val newPosts = szurubooru.listAllPosts(config.triggerTag)
        log("There are ${newPosts.size} posts that needs to be tagged")
//      val managedPosts = szurubooru.listAllPosts(config.managedTag)
//      log("There are ${managedPosts.size} posts that are managed by tagger")

        newPosts.forEach {
            if (it.isImage() == false) {
                logErr("Post ${it.id} is not an image.");
                szurubooru.updatePostTags(it.id, config.errorTag)
                return@forEach
            }

            log("Searching IQDB match for post ${it.id}...");
            val imageFile = createTempFile()
            imageFile.writeBytes(Jsoup.connect(it.contentUrl).timeout(30 * 1000)
                    .ignoreContentType(true).execute().bodyAsBytes())
            val sourceImageUrl = queryIqdb(imageFile);
            imageFile.delete()

            if (sourceImageUrl == null) {
                log("Post ${it.id} not found in the IQDB datebase.");
                val newTagsList = it.tags.toMutableList()
                newTagsList.remove(config.triggerTag)
                newTagsList.add(config.noMatchTag)
                szurubooru.updatePostTags(it.id, *newTagsList.toTypedArray())
            } else {
                log("Post ${it.id} found match: " + sourceImageUrl);
            }

            Thread.sleep(500)
        }
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
            exitProcess(1);
        } catch (e: IOException) {
            e.printStackTrace()
            exitProcess(2);
        }
    }
}
