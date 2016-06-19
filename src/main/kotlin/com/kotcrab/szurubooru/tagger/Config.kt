package com.kotcrab.szurubooru.tagger

import java.util.*

/**
 * Refer to `config.default.yaml` for more complete description.
 * @author Kotcrab
 */

/** Represents single remapping. */
class RemapDto {
    var from = ""
    var to = ""
}

/** Danbooru configuration. */
class DanbooruDto {
    var anonymous = true
    var username = "your-username"
    var apiKey = "your-api-key"
    var hourRequestLimit = 500
}

/** Tag escaping configuration. */
class TagEscapingDto {
    var escapeCharacters = "[]{}/\\<>=+;@|!?.'"
    var escapeWith = "_"
    var ignoreFirstColon = true
    var removeLastDot = true
}

/** Szurubooru configuration. */
class SzurubooruDto {
    var apiPath = "http://szurubooru.local/api/"
    var dataPath = "http://szurubooru.local/data/"
    var username = "auto-tagger"
    var password = "auto-tagger"
}

/** Single instance check configuration. */
class SingleInstanceDto {
    var enabled = true
    var port = 54212
}

/** Tags configuration. */
class TagsDto {
    var obtainAliases = true
    var obtainSuggestions = false
    var obtainImplications = true
    var tagMapFile = "tagMap.yaml"
    var tagEscaping: TagEscapingDto = TagEscapingDto()
    var ignoreTags: List<String> = ArrayList()
    var remapCategories: List<RemapDto> = ArrayList()
}

class ConfigDto {
    var triggerTag = "auto_tagme"
    var managedTag = "auto_tagged"
    var errorTag = "auto_tagger_error"
    var noMatchTag = "tagme"
    var batchUploadTag = "auto_tagme"

    var checkBooruConnectivity = true
    var storeSourceUrl = true
    var updateImageRating = true
    var updateImageNotes = true

    var tags = TagsDto()
    var singleInstance = SingleInstanceDto()
    var szurubooru = SzurubooruDto()
    var danbooru = DanbooruDto()
}
