package com.kotcrab.szurubooru.tagger

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.jsoup.Connection
import org.jsoup.HttpStatusException
import org.jsoup.Jsoup
import java.io.FileInputStream
import java.util.concurrent.TimeUnit

/**
 * [Jsoup] wrapper. [Jsoup] is originally designed to parsing website HTML but even using it for querying REST APIs
 * is very convenient.
 * @author Kotcrab */
class RestClient(private val basicHttpAuth: String? = null) {
    companion object {
        val NORMAL_TIMEOUT = TimeUnit.SECONDS.toMillis(3).toInt()
        val LONG_TIMEOUT = TimeUnit.SECONDS.toMillis(10).toInt()
    }

    private val jsonParser = JsonParser()
    var requestCounter = 0

    fun get(requestUrl: Array<String>, timeout: Int = NORMAL_TIMEOUT): JsonElement {
        return jsonParser.parse(request(requestUrl.join(), Connection.Method.GET, timeout).executeSafely())
    }

    fun post(requestUrl: Array<String>, args: Array<PostArg>, timeout: Int = LONG_TIMEOUT): String {
        val request = request(requestUrl.join(), Connection.Method.POST, timeout)
        args.forEach { it.apply(request) }
        return request.executeSafely()
    }

    fun put(requestUrl: Array<String>, json: JsonObject, timeout: Int = NORMAL_TIMEOUT): String {
        return request(requestUrl.join(), Connection.Method.PUT, timeout).requestBody(json.toString()).executeSafely()
    }

    private fun request(url: String, method: Connection.Method, timeout: Int): Connection {
        val request = Jsoup.connect(url).validateTLSCertificates(false).ignoreHttpErrors(true).ignoreContentType(true)
                .method(method).timeout(timeout)
        if (basicHttpAuth != null) request.header("Authorization", "Basic $basicHttpAuth")
        return request
    }

    private fun Array<String>.join(): String {
        return joinToString(separator = "")
    }

    private fun Connection.executeSafely(): String {
        val response = execute()
        requestCounter++
        val statusCode = response.statusCode()
        if (statusCode != 200) {
            throw HttpStatusException("HTTP error fetching URL. Returned request body: \"${response.body()}\"", statusCode, response.url().toString())
        }
        return response.body()
    }
}

interface PostArg {
    fun apply(request: Connection)
}

class StringPostArg(private val key: String, private val value: String) : PostArg {
    override fun apply(request: Connection) {
        request.data(key, value)
    }
}

class FilePostArg(private val key: String, private val fileName: String, private val inputStream: FileInputStream) : PostArg {
    override fun apply(request: Connection) {
        request.data(key, fileName, inputStream)
    }
}
