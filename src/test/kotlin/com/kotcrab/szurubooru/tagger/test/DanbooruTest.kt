package com.kotcrab.szurubooru.tagger.test

import com.kotcrab.szurubooru.tagger.Danbooru
import com.kotcrab.szurubooru.tagger.DanbooruDto
import org.junit.Assert.*
import org.junit.Test

/**
 * Danbooru integration test. This test has to assume that example resources used for testing won't change.
 * @author Kotcrab
 */
class DanbooruTest {
    @Test
    fun testGetPost() {
        assertTrue(Danbooru(DanbooruDto()).getPost(2376896).json.toString().startsWith("""{"id":2376896,"""))
    }

    @Test
    fun testGetPostTags() {
        val post = Danbooru(DanbooruDto()).getPost(2376896)
        assertTrue(post.characterTags.contains("caster_(fate/extra)"))
        assertTrue(post.generalTags.contains("fox_ears"))
        assertTrue(post.copyrightTags.contains("fate/extra"))
    }

    @Test
    fun testGetPostRating() {
        assertEquals(Danbooru(DanbooruDto()).getPost(2376896).rating, Danbooru.Rating.Safe)
    }

    @Test
    fun testGetTag() {
        val tag = Danbooru(DanbooruDto()).getTag("fox_ears")
        assertEquals(tag.name, "fox_ears")
        assertEquals(tag.category, Danbooru.TagCategory.General)
        assertTrue(tag.implications.contains("animal_ears"))
        assertTrue(tag.aliases.contains("kitsunemimi"))
        assertNotEquals(tag.relatedTags[0], "fox_ears")
        assertNotEquals(tag.relatedTags[1], "1")
        assertTrue(tag.relatedTags.contains("animal_ears")) //this is unlikely to change but it's possible
    }


    @Test
    fun testIdFromUrl() {
        val danbooru = Danbooru(DanbooruDto())
        assertEquals(danbooru.idFromUrl("https://danbooru.donmai.us/posts/2376896/"), 2376896)
        assertEquals(danbooru.idFromUrl("https://danbooru.donmai.us/posts/2376896"), 2376896)
        assertEquals(danbooru.idFromUrl("danbooru.donmai.us/posts/2376896/"), 2376896)
        assertEquals(danbooru.idFromUrl("danbooru.donmai.us/posts/2376896"), 2376896)
    }
}
