package com.kotcrab.szurubooru.tagger.test

import com.kotcrab.szurubooru.tagger.Szurubooru
import com.kotcrab.szurubooru.tagger.SzurubooruDto
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Ignore
import org.junit.Test

/**
 * Szurubooru integration test, due to need of having local instance of Szurubooru running, those tests are disabled by default.
 * @author Kotcrab
 */
@Ignore
class SzurubooruTest {
    @Test
    fun testHostReachable() {
        assertTrue(Szurubooru(getLocalDto()).isHostReachable())
    }

    @Test
    fun testHostNotReachable() {
        assertFalse(Szurubooru(SzurubooruDto()).isHostReachable())
    }

    @Test
    fun testAuthorized() {
        assertTrue(Szurubooru(getLocalDto()).isAuthorized())
    }

    @Test
    fun testNotAuthorizedNoSuchUser() {
        val dto = getLocalDto()
        dto.username = "not-a-user"
        dto.password = "not-a-password"
        assertFalse(Szurubooru(dto).isAuthorized())
    }

    @Test
    fun testNotAuthorizedInvalidPassword() {
        val dto = getLocalDto()
        dto.username = "auto-tagger"
        dto.password = "not-a-password"
        assertFalse(Szurubooru(dto).isAuthorized())
    }

    @Test
    fun testGetTagCategories() {
        Szurubooru(getLocalDto()).getTagCategories()
    }

    @Test
    @Ignore
    fun batchUpload() {
//      Szurubooru(getLocalDto()).post("post/2/").ignoreHttpErrors(true).method(Connection.Method.DELETE).execute().body()
//        val szuru = Szurubooru(getLocalDto())
//        File("""C:\Users\Kotcrab\Desktop\boorutest""").listFiles().forEach {
//            println("Upload ${it.name}")
//            szuru.uploadFile(it, Szurubooru.Safety.Safe, "auto_tagme")
//        }
    }

    private fun getLocalDto(): SzurubooruDto {
        val dto = SzurubooruDto();
        dto.apiPath = "http://192.168.73.132/api/"
        return dto;
    }
}
