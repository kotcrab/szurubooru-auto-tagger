package com.kotcrab.szurubooru.tagger;

class RemapDto {
    lateinit var from: String
    lateinit var to: String
}

class DanbooruDto {
    var anonymous: Boolean = true
    lateinit var username: String
    lateinit var apiKey: String
}

class SzurubooruDto {
    lateinit var apiPath: String
    lateinit var username: String
    lateinit var password: String
}

class SingleInstanceDto {
    var enabled: Boolean = false
    var port: Int = 0
}

class TagsDto {
    lateinit var ignoreTags: List<String>
    lateinit var remapTags: List<RemapDto>
    lateinit var remapCategories: List<RemapDto>
}

class ConfigDto {
    lateinit var triggerTag: String
    lateinit var taggedTag: String
    lateinit var errorTag: String
    var storeSourceUrl: Boolean = true

    lateinit var tags: TagsDto
    lateinit var singleInstance: SingleInstanceDto
    lateinit var szurubooru: SzurubooruDto
    lateinit var danbooru: DanbooruDto
}
