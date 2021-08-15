package com.kotcrab.szurubooru.tagger

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import java.io.File
import kotlin.system.exitProcess

/** @author Kotcrab */

fun main(args: Array<String>) {
    var argConfigPath: String? = null
    var task: AutoTagger.Task = AutoTagger.Task.NewPosts
    var taskArguments: List<String>? = null

    args.forEachIndexed { i, arg ->
        if (arg == "-h" || arg == "--help") {
            printHelp()
            exitProcess(0)
        }

        if ((arg == "-c" || arg == "--config")) {
            if (i + 1 >= args.size) throw IllegalStateException("You must specify config file path")
            argConfigPath = args[i + 1]
            return@forEachIndexed
        }

        if ((arg == "-t" || arg == "--task")) {
            if (i + 2 > args.size) throw IllegalStateException("You must specify task name")
            task = AutoTagger.Task.valueOf(args[i + 1])
            if (task.hasArgument) {
                if (i + 2 >= args.size) throw IllegalStateException("You must specify task arguments")
                taskArguments = args.slice(i + 2..args.size - 1)
            }
            return@forEachIndexed
        }
    }

    if (argConfigPath != null && !File(argConfigPath).exists()) {
        log("Config file $argConfigPath does not exist.")
        return
    }

    val jarPath = JarUtils.getJarPath(AutoTagger::class.java);
    val configPaths = arrayOf(argConfigPath, "config.yaml", "config.default.yaml", "${jarPath}config.yaml", "${jarPath}config.default.yaml")
    val configPath: String? = configPaths.firstOrNull { it != null && File(it).exists() }

    if (configPath == null) {
        log("Config file does not exist.")
        return
    }

    log("Szurubooru auto tagger")
    log("Using config from $configPath")

    val autoTagger = AutoTagger(loadConfig(configPath), File(configPath).absoluteFile.parentFile)
    autoTagger.run(task, taskArguments)
    autoTagger.dispose()
}

fun printHelp() {
    log("Szurubooru auto tagger. Usage: [-c config-file-path] [-t task task-argument1 task-argument2 task-argument3 ...]")
    log("config-file-path: (-c, --config) Path to configuration file. If not specified config.yaml is used or config.default.yaml if former does not exist.")
    log("task: (-t, --task) Optional, task that auto tagger will perform. By default 'NewPosts' is used. This parameter can be: ")
    AutoTagger.Task.values().forEach { log("\t${it.name} - ${it.description}") }
    log("task-arguments: Optional, only needed if tasks requires passing argument. Must be specified as last config parameter")
    exitProcess(0)
}

fun loadConfig(path: String): ConfigDto {
    val yamlMapper = ObjectMapper(YAMLFactory())
        .registerModule(KotlinModule())
    return yamlMapper.readValue(File(path))
}

