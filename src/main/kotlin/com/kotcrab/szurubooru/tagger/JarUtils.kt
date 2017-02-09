package com.kotcrab.szurubooru.tagger

import java.io.File
import java.io.UnsupportedEncodingException
import java.net.URLDecoder

/**
 * Jar related utils.
 * @author Kotcrab
 */
object JarUtils {
    fun getJarPath(caller: Class<*>): String {
        try {
            val url = caller.protectionDomain.codeSource.location
            var path = URLDecoder.decode(url.file, "UTF-8")

            // remove jar name from path
            if (System.getProperty("os.name").toLowerCase().contains("win"))
                path = path.substring(1, path.lastIndexOf('/')) // cut first '/' for Windows
            else
                path = path.substring(0, path.lastIndexOf('/'))

            if (path.endsWith("target/classes"))
            //launched from ide
                path = path.substring(0, path.length - "/target/classes".length)

            path = path.replace("/", File.separator)
            return path + File.separator
        } catch (e: UnsupportedEncodingException) {
            throw IllegalStateException("Failed to get jar path due to unsupported encoding!", e)
        }

    }
}
