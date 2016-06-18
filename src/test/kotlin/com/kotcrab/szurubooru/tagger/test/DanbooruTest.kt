package com.kotcrab.szurubooru.tagger.test

import com.kotcrab.szurubooru.tagger.Danbooru
import com.kotcrab.szurubooru.tagger.DanbooruDto
import com.kotcrab.szurubooru.tagger.Szurubooru
import org.junit.Assert.*
import org.junit.Test

/**
 * Danbooru integration test. This test has to assume that example resources used for testing won't change.
 * @author Kotcrab
 */
class DanbooruTest {
    @Test
    fun testGetPost() {
        val post = Danbooru(DanbooruDto()).getPost(2376896)
        assertTrue(post.json.toString().startsWith("""{"id":2376896,"""))
        assertTrue(post.id == 2376896)
        assertTrue(post.width != 0)
        assertTrue(post.height != 0)
        assertFalse(post.hasNotes)
        assertTrue(Danbooru(DanbooruDto()).getPost(2079472).hasNotes)
    }

    @Test
    fun testGetPostTags() {
        val post = Danbooru(DanbooruDto()).getPost(2376896)
        assertTrue(post.tags.contains("caster_(fate/extra)"))
        assertTrue(post.characterTags.contains("caster_(fate/extra)"))
        assertTrue(post.generalTags.contains("fox_ears"))
        assertTrue(post.copyrightTags.contains("fate/extra"))
    }

    @Test
    fun testGetPostNotes() {
        val notes = Danbooru(DanbooruDto()).getPostNotes(2174127)
        assertTrue(notes.size != 0)
        assertTrue(notes.filter { it.body.contains("Good Luck") }.size != 0)
        assertTrue(notes[0].active)
        assertTrue(notes[0].x != 0)
        assertTrue(notes[0].y != 0)
        assertTrue(notes[0].width != 0)
        assertTrue(notes[0].height != 0)
    }

    @Test
    fun testGetPostRating() {
        assertEquals(Danbooru(DanbooruDto()).getPost(2376896).rating, Danbooru.Rating.Safe)
        assertEquals(Danbooru(DanbooruDto()).getPost(2376896).rating.toSzurubooruSafety(), Szurubooru.Safety.Safe)
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
        assertEquals(danbooru.postIdFromUrl("https://danbooru.donmai.us/posts/2376896/"), 2376896)
        assertEquals(danbooru.postIdFromUrl("https://danbooru.donmai.us/posts/2376896"), 2376896)
        assertEquals(danbooru.postIdFromUrl("danbooru.donmai.us/posts/2376896/"), 2376896)
        assertEquals(danbooru.postIdFromUrl("danbooru.donmai.us/posts/2376896"), 2376896)
    }
}
