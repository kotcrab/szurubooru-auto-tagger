package com.kotcrab.szurubooru.tagger.test

import com.kotcrab.szurubooru.tagger.Danbooru
import com.kotcrab.szurubooru.tagger.DanbooruDto
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * @author Kotcrab
 */
class BooruTest {
    @Test
    fun testDanbooruGetPost() {
        assertTrue(Danbooru(DanbooruDto()).getPost(2376896).startsWith("""{"id":2376896,"""))
    }
}
