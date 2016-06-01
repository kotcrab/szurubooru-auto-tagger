package com.kotcrab.szurubooru.tagger;

import java.util.*

class RemapDto {
    var from = ""
    var to = ""
}

class DanbooruDto {
    var anonymous = true
    var username = "your-username"
    var apiKey = "your-api-key"
}

class SzurubooruDto {
    var apiPath = "https://szurubooru.local/api"
    var username = "auto-tagger"
    var password = "auto-tagger"
}

class SingleInstanceDto {
    var enabled = true
    var port = 54212
}

class TagsDto {
    var obtainAliases = true
    var obtainSuggestions = true
    var obtainImplications = false;
    var ignoreTags: List<String> = ArrayList();
    var remapTags: List<RemapDto> = ArrayList();
    var remapCategories: List<RemapDto> = ArrayList();
}

class ConfigDto {
    var triggerTag = "auto_tagme"
    var taggedTag = "auto_tagged"
    var errorTag = "auto_tagger_error"
    var storeSourceUrl = true

    var tags = TagsDto()
    var singleInstance = SingleInstanceDto()
    var szurubooru = SzurubooruDto()
    var danbooru = DanbooruDto()
}
