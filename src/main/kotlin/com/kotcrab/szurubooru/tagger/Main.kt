package com.kotcrab.szurubooru.tagger

import com.esotericsoftware.yamlbeans.YamlReader
import java.io.File
import java.io.FileReader

/** @author Kotcrab */

fun main(args: Array<String>) {
    var path: String
    if (args.size == 0) {
        path = "config.yaml"
        if (File(path).exists() == false)
            path = "config.default.yaml"
    } else if (args.size == 1) {
        path = args[0]
    } else {
        log("Szurubooru auto tagger. Usage: <config-file-path>")
        log("config-file-path: Path to configuration file. If not specified config.yaml is used or config.default.yaml if former does not exist.")
        return
    }

    if (File(path).exists() == false) {
        log("Config file $path does not exist.")
        return
    }

    log("Szurubooru auto tagger")
    log("Using config from $path")

    val autoTagger = AutoTagger(loadConfig(path))
    autoTagger.synchronizeTags()
    autoTagger.dispose()
}

fun loadConfig(path: String): ConfigDto {
    val reader = YamlReader(FileReader(path))
    return reader.read(ConfigDto::class.java)
}

