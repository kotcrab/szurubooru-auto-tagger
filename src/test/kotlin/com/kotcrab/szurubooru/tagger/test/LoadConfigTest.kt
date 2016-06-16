package com.kotcrab.szurubooru.tagger.test

import com.kotcrab.szurubooru.tagger.loadConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/** @author Kotcrab */
internal class LoadConfigTest {
    @Test
    fun testLoadConfig() {
        val config = loadConfig("config.default.yaml")
        assertEquals(config.triggerTag, "auto_tagme")
        assertNotNull(config.singleInstance)
        assertNotNull(config.danbooru)
        assertNotNull(config.szurubooru)
        assertNotNull(config.tags)
        assertNotNull(config.tags.remapTags)
        assertNotNull(config.tags.remapCategories)
        assertNotNull(config.tags.ignoreTags)
    }
}
