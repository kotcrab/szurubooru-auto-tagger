package com.kotcrab.szurubooru.tagger

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.jsoup.Connection
import org.jsoup.HttpStatusException
import org.jsoup.Jsoup
import java.io.FileInputStream
import java.net.SocketTimeoutException
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.*
import java.util.concurrent.TimeUnit

/**
 * [Jsoup] wrapper. [Jsoup] is originally designed to parsing website HTML but even using it for querying REST APIs
 * is very convenient.
 * @author Kotcrab
 */
class RestClient(private val basicHttpAuth: String? = null, private val requestsPerHour: Int = -1) {
    companion object {
        val NORMAL_TIMEOUT = TimeUnit.SECONDS.toMillis(3).toInt()
        val LONG_TIMEOUT = TimeUnit.SECONDS.toMillis(10).toInt()
    }

    private val jsonParser = JsonParser()

    private var startTime: LocalDateTime
    private var requestCounter = 0

    init {
        startTime = LocalDateTime.now()
    }

    fun get(requestUrl: Array<String>, timeout: Int = NORMAL_TIMEOUT): JsonElement {
        return jsonParser.parse(request(requestUrl.join(), Connection.Method.GET, timeout).executeSafely())
    }

    fun post(requestUrl: Array<String>, args: Array<PostArg>, timeout: Int = LONG_TIMEOUT): String {
        val request = request(requestUrl.join(), Connection.Method.POST, timeout)
        args.forEach { it.apply(request) }
        return request.executeSafely()
    }

    fun post(requestUrl: Array<String>, json: String, timeout: Int = LONG_TIMEOUT): String {
        val request = request(requestUrl.join(), Connection.Method.POST, timeout)
        request.requestBody(json)
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
        var response: Connection.Response? = null
        for (i in 1..5) {
            try {
                response = execute()
                requestCounter++
                break
            } catch (e: SocketTimeoutException) {
                log("URL ${request().url().toString()} timed out. Retrying in 3 seconds...")
                Thread.sleep(3000)
            }
        }

        if (requestsPerHour != -1) {
            val minutes = ChronoUnit.MINUTES.between(startTime, LocalDateTime.now())
            if (minutes > 60) {
                requestCounter = 0
                startTime = LocalDateTime.now()
            }

            if (requestCounter >= requestsPerHour) {
                val sleepTime = 60 - minutes
                log("Request limit exceeded, sleeping for $sleepTime minutes")
                Thread.sleep(TimeUnit.MINUTES.toMillis(sleepTime))
                requestCounter = 0
                startTime = LocalDateTime.now()
            }
        }

        if (response == null) throw IllegalStateException("URL `${request().url().toString()}` timed out after 5 retries")
        val statusCode = response.statusCode()
        if (statusCode != 200) {
            throw HttpStatusException("HTTP error fetching URL. Returned request body: \"${response.body()}\"", statusCode, response.url().toString())
        }
        return response.body()
    }
}

/** @param fetchPage fetch next page and return list of json elements, Int is page number */
class PagedResource<T>(val fetchPage: (Int) -> List<JsonElement>, val transform: (JsonElement) -> T) {
    var results = emptyList<T>()
    var page = 0
        private set
    var done = false
        private set

    init {
        fetchNextPage()
    }

    fun fetchNextPage() {
        page++
        results = fetchPage.invoke(page).map(transform)
        if (results.size == 0) done = true
    }

    fun forEachPage(consumer: (List<T>) -> Unit) {
        while (true) {
            consumer.invoke(results)
            fetchNextPage()
            if (done) break
        }
    }

    fun toList(): List<T> {
        val list = ArrayList<T>()
        forEachPage { list.addAll(it) }
        return list
    }
}

/** @param fetchPage fetch next page and return list of json elements, Int is page number */
class IterablePagedResource<T>(val fetchPage: (Int) -> List<JsonElement>, val transform: (JsonElement) -> T) : AbstractIterator<T>() {
    private var results = emptyList<JsonElement>()
    private var pos = 0

    var page = 0
        private set
    var pageJustFetched: Boolean = false
        private set
    var pageSize = 0
        private set

    init {
        fetchNextPage()
    }

    private fun fetchNextPage() {
        page++
        pos = 0
        results = fetchPage.invoke(page)
        pageSize = results.size
    }

    override fun computeNext() {
        pageJustFetched = false
        if (pos == results.size) {
            fetchNextPage()
            pageJustFetched = true

            if (results.size == 0) {
                done()
                return
            }
        }
        setNext(transform(results[pos++]))
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
