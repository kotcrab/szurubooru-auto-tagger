package com.kotcrab.szurubooru.tagger

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
        val managedPosts = szurubooru.listAllPosts(config.managedTag)
        log("There are ${managedPosts.size} posts that are managed by tagger")
        //check post.type == "image"
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
            System.err.println("Another instance is already running or port ${config.singleInstance.port} is being used by other application.")
            System.exit(1);
        } catch (e: IOException) {
            e.printStackTrace()
            exitProcess(2);
        }
    }
}
