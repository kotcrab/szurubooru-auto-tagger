package com.kotcrab.szurubooru.tagger

import java.io.IOException
import java.net.BindException
import java.net.InetAddress
import java.net.ServerSocket
import kotlin.system.exitProcess

/** @author Kotcrab */
class AutoTagger(private val config: ConfigDto) {
    private lateinit var lockSocket: ServerSocket

    init {
        if (config.singleInstance.enabled) {
            checkIfRunning();
        }
    }

    fun synchronizeTags() {

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
