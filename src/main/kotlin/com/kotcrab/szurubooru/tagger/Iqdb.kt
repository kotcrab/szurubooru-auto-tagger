package com.kotcrab.szurubooru.tagger

import org.jsoup.Jsoup
import java.io.File
import java.io.FileInputStream
import java.util.*

/** @author Kotcrab */

/** Enum of all Iqdb services that can be used. */
enum class IqdbServices(val id: Int) {
    Danbooru(1),
    Konachan(2),
    Yandere(3),
    Gelbooru(4),
    SanakuComplex(5),
    EShuushuu(6),
    TheAnimeGallery(10),
    Zerochan(10),
    MangaDrawing(12),
    AnimePictures(13),
}

/**
 * Performs Iqdb query
 * @param file image file that will be send
 * @param services list of enabled services, by default only Danbooru is used
 * @return image url if Iqdb found match or null if no match was found
 */
fun queryIqdb(file: File, services: EnumSet<IqdbServices> = EnumSet.of(IqdbServices.Danbooru)): String? {
    val connection = Jsoup.connect("https://iqdb.org/")
            .data("file", file.name, FileInputStream(file))
            .validateTLSCertificates(false).timeout(30 * 1000)
    services.forEach { service -> connection.data("service[]", service.id.toString()) }
    val document = connection.post()
    val bestMatchHeader = document.select("tr:contains(Best match)")
    if (bestMatchHeader.size == 0) return null
    val url = bestMatchHeader.parents()[0].select("td.image > a").attr("href")
    if (url.startsWith("//")) return "https:" + url
    return url
}
