package com.kotcrab.szurubooru.tagger

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

/**
 * Refer to `config.default.yaml` for more complete description.
 * @author Kotcrab
 */

/** Represents single remapping. */
@JsonIgnoreProperties(ignoreUnknown = true)
class RemapDto {
    var from = ""
    var to = ""
}

/** Danbooru configuration. */
@JsonIgnoreProperties(ignoreUnknown = true)
class DanbooruDto {
    var anonymous = true
    var username = "your-username"
    var apiKey = "your-api-key"
    var hourRequestLimit = 500
}

/** Tag escaping configuration. */
@JsonIgnoreProperties(ignoreUnknown = true)
class TagEscapingDto {
    var escapeCharacters = "/+"
    var escapeWith = "_"
}

/** Szurubooru configuration. */
@JsonIgnoreProperties(ignoreUnknown = true)
class SzurubooruDto {
    var apiPath = "http://szurubooru.local/api/"
    var dataPath = "http://szurubooru.local/data/"
    var username = "auto-tagger"
    var password = "auto-tagger"
}

/** Single instance check configuration. */
@JsonIgnoreProperties(ignoreUnknown = true)
class SingleInstanceDto {
    var enabled = true
    var port = 54212
}

/** Tags configuration. */
@JsonIgnoreProperties(ignoreUnknown = true)
class TagsDto {
    var obtainAliases = true
    var obtainSuggestions = false
    var obtainImplications = true
    var tagMapFile = "tagMap.yaml"
    var tagEscaping: TagEscapingDto = TagEscapingDto()
    var ignoreTags: List<String> = ArrayList()
    var remapCategories: List<RemapDto> = ArrayList()
}

@JsonIgnoreProperties(ignoreUnknown = true)
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
    var createCommentWhenBiggerImageFound = true

    var tags = TagsDto()
    var singleInstance = SingleInstanceDto()
    var szurubooru = SzurubooruDto()
    var danbooru = DanbooruDto()
}
