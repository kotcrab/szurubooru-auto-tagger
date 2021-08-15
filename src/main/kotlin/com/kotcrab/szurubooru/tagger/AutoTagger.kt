package com.kotcrab.szurubooru.tagger

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import com.github.salomonbrys.kotson.jsonArray
import com.github.salomonbrys.kotson.jsonObject
import com.google.gson.JsonObject
import com.overzealous.remark.IgnoredHtmlElement
import com.overzealous.remark.Options
import com.overzealous.remark.Remark
import org.jsoup.Jsoup
import java.io.File
import java.io.FileFilter
import java.io.IOException
import java.net.BindException
import java.net.InetAddress
import java.net.ServerSocket
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import kotlin.system.exitProcess

/** @author Kotcrab */
class AutoTagger(private val config: ConfigDto, private val workingDir: File) {
    private lateinit var lockSocket: ServerSocket
    private val yamlMapper = ObjectMapper(YAMLFactory())
        .registerModule(KotlinModule())

    val danbooru = Danbooru(config.danbooru)
    val szurubooru = Szurubooru(config.szurubooru)
    val remark by lazy {
        val options = Options.markdown()
        options.inWordEmphasis
        options.getIgnoredHtmlElements().add(IgnoredHtmlElement.create("tn"))
        Remark(options)
    }

    val mimeTypeExtensionMap = mapOf(
            "image/gif" to "gif",
            "image/jpeg" to "jpg",
            "image/png" to "png",
            "image/x-png" to "png",
            "image/bmp" to "bmp",
            "image/x-windows-bmp" to "bmp",
            "image/tiff" to "tiff"
    )

    var szuruTags: List<String>
    var szuruTagCategories: List<String>
    var tagNameRegex: Regex

    var tagMap: MutableMap<String, String>

    init {
        if (config.singleInstance.enabled) {
            checkIfRunning()
        }

        if (config.checkBooruConnectivity) {
            if (szurubooru.isHostReachable() == false) throw IllegalStateException("Can't to connect to Szurubooru using API URL: ${config.szurubooru.apiPath}")
            if (szurubooru.isAuthorized() == false) throw IllegalStateException("Failed to authorize to Szurubooru using provided credentials: ${config.szurubooru.username}")
            if (danbooru.isAuthorized() == false) throw IllegalStateException("Failed to authorize to Danbooru using provided credentials: ${config.danbooru.username}")
            log("Booru connectivity looks ok")
        } else {
            log("Booru connectivity check skipped")
        }

        log("Reading tag map...")
        tagMap = readTagMap()

        log("Obtaining tags...")
        szuruTags = szurubooru.getTags()
        szuruTagCategories = szurubooru.getTagCategories()
        tagNameRegex = Regex(szurubooru.getInfo().tagNameRegex)

        val missingCategories = Danbooru.TagCategory.values().filterNot { szuruTagCategories.contains(remapTagCategory(it.remapName)) }
        if (missingCategories.size != 0) throw IllegalStateException("Szurubooru is missing required tag categories: ${missingCategories.joinToString { it.remapName }}. " +
                "Make sure they exist or modify tag category remapping configuration.")
    }

    fun readTagMap(): MutableMap<String, String> {
        val tagMapFile = workingDir.child(config.tags.tagMapFile)
        if (!tagMapFile.exists()) {
            return mutableMapOf()
        }
        return yamlMapper.readValue(tagMapFile)
    }

    fun saveTagMap() {
        val tagMapFile = workingDir.child(config.tags.tagMapFile)
        yamlMapper.writeValue(tagMapFile, tagMap)
    }

    fun run(task: Task, taskArguments: List<String>?) {
        when (task) {
            Task.NewPosts -> {
                val newPosts = szurubooru.pagedPosts(config.triggerTag).toList()
                log("There are ${newPosts.size} posts that needs to be tagged")
                runForPosts(newPosts, true)
            }

            Task.ExistingPosts -> {
                val managedPosts = szurubooru.pagedPosts(config.managedTag)
                log("Updating tags of existing posts.")
                managedPosts.forEachPage {
                    log("Page ${managedPosts.page}, page size ${it.size}")
                    runForPosts(it, false)
                }
            }

            Task.Posts -> {
                if (taskArguments == null) throw IllegalStateException("You must specify post id for selected task")
                try {
                    runForPosts(taskArguments.map { szurubooru.getPost(it.toInt()) }, false)
                } catch(e: NumberFormatException) {
                    throw IllegalStateException("Post ids must be numbers", e)
                }
            }

            Task.NewTags -> {
                runForAllTags(true)
                saveTagMap()
            }
            Task.ExistingTags -> {
                runForAllTags(false)
                saveTagMap()
            }

            Task.Tags -> {
                if (taskArguments == null) throw IllegalStateException("You must specify tag name for selected task")
                taskArguments.forEach {
                    try {
                        updateTag(reverseTagRemap(it))
                        log("Updated tag $it.")
                    } catch(e: Exception) {
                        logErr("Error while updating tag $it.")
                        e.printStackTrace()
                    }
                }
            }

            Task.Notes -> {
                if (taskArguments == null) throw IllegalStateException("You must specify post id for selected task")
                try {
                    taskArguments.forEach {
                        val szuruPost = szurubooru.getPost(it.toInt())
                        log("Searching IQDB match for post ${szuruPost.id}...")
                        val sourceImageUrl = szurubooru.searchPostOnIqdb(szuruPost)
                        if (sourceImageUrl == null) {
                            log("Post ${szuruPost.id} not found in the IQDB datebase.")
                            return@forEach
                        }
                        log("Found post ${szuruPost.id} match: $sourceImageUrl")
                        val danPost = danbooru.getPost(sourceImageUrl)
                        try {
                            updatePostNote(PostSet(szuruPost, danPost))
                            log("Updated post ${szuruPost.id} notes.")
                        } catch(e: Exception) {
                            logErr("Error occurred while updating post ${szuruPost.id} notes")
                            e.printStackTrace()
                        }
                    }
                } catch(e: NumberFormatException) {
                    throw IllegalStateException("Post ids must be numbers", e)
                }
            }

            Task.BatchUpload -> {
                if (taskArguments == null) throw IllegalStateException("You must specify source directory for selected task")
                val sourceDir = File(taskArguments.first())
                log("Batch upload mode. Source ${sourceDir.absolutePath}")
                if (sourceDir.exists() == false) throw IllegalStateException("Provided directory path does not exist")
                val files = sourceDir.listFiles(FileFilter { arrayOf("jpg", "jpeg", "png", "bmp").contains(it.extension) })
                if (files.size == 0) {
                    log("Source directory is empty")
                    return
                }
                val uploadedDir = sourceDir.child("uploaded")
                uploadedDir.mkdir()
                files.forEachIndexed { index, file ->
                    try {
                        szurubooru.uploadFile(file, Szurubooru.Safety.Safe, config.batchUploadTag)
                        Files.move(file.toPath(), uploadedDir.child(file.name).toPath(), StandardCopyOption.REPLACE_EXISTING)
                        log("Uploaded file ${file.name}. Completed ${index + 1}/${files.size}")
                    } catch(e: Exception) {
                        logErr("Error occurred while uploading file ${file.name}")
                        e.printStackTrace()
                    }
                }
            }
            Task.BatchDownload -> {
                if (taskArguments == null) throw IllegalStateException("You must search query and optionally output directory")
                val query = taskArguments.first()
                val posts = szurubooru.iterablePosts(query)
                if (posts.hasNext() == false) {
                    log("No files to download matching query: `$query`")
                    return
                }
                val output = if (taskArguments.size > 1) File(taskArguments[1]) else Paths.get("").toFile().child("batchDownloader")
                output.mkdirs()
                log("Downloading to ${output.absolutePath}")
                posts.forEach { post ->
                    if (posts.pageJustFetched) {
                        log("Page ${posts.page}, page size ${posts.pageSize}")
                    }

                    try {
                        val extension = mimeTypeExtensionMap[post.mimeType]
                        if (extension == null) {
                            log("Ignoring ${post.id}, extension for image type ${post.mimeType} is unknown")
                            return@forEach
                        }
                        val outFile = output.child("${post.id} - ${post.safety.toString().substring(0, 1).toLowerCase()} - ${post.tags.joinToString(limit = 5)}.$extension")
                        outFile.writeBytes(Jsoup.connect(post.contentUrl).maxBodySize(0).timeout(30 * 1000)
                                .ignoreContentType(true).execute().bodyAsBytes())
                        log("Downloaded post ${post.id}")
                    } catch(e: Exception) {
                        logErr("Error occurred while downloading post ${post.id}")
                        e.printStackTrace()
                    }
                }
            }
        }
        log("Done.")
    }

    private fun runForAllTags(onlyNeverEdited: Boolean) {
        val tags = szurubooru.iterableTags("*")
        for ((index, tag) in tags.withIndex()) {
            if (tags.pageJustFetched) {
                log("Page ${tags.page}, page size ${tags.pageSize}")
            }
            if (onlyNeverEdited && tag.wasEdited) {
                log("Skipped tag ${tag.name}. Completed ${index + 1}/${tags.pageSize} on current page.")
                continue
            }

            if (config.tags.ignoreTags.contains(tag.name)) {
                log("Skipped tag ${tag.name} (tag is on ignore list). Completed ${index + 1}/${tags.pageSize} on current page.")
                continue
            }

            try {
                updateTag(reverseTagRemap(tag.name))
                log("Updated tag ${tag.name}. Completed ${index + 1}/${tags.pageSize} on current page.")
            } catch(e: Exception) {
                logErr("Error occurred while updating tag ${tag.name}")
                e.printStackTrace()
            }
        }
    }

    private fun runForPosts(posts: List<Szurubooru.Post>, newPostsOnly: Boolean) {
        val createdTags = HashSet<TagSet>()
        val postsToBeNoted = ArrayList<PostSet>()
        updatePostsTags(posts, postsToBeNoted, createdTags, newPostsOnly)
        saveTagMap()
        updatePostsNotes(postsToBeNoted)
        updateTags(createdTags)
        saveTagMap()
    }

    private fun updatePostsTags(posts: List<Szurubooru.Post>, postsToBeNoted: ArrayList<PostSet>, createdTags: HashSet<TagSet>, newPostsOnly: Boolean) {
        posts.forEachIndexed { i, post ->
            try {
                updatePostTags(post, postsToBeNoted, createdTags, newPostsOnly)
                log("Updated post ${post.id} tags. Completed ${i + 1}/${posts.size}.")
            } catch(e: Exception) {
                logErr("Error occurred while updating post ${post.id} tags")
                e.printStackTrace()

                try {
                    replacePostTriggerTag(post, config.errorTag)
                } catch(e: Exception) {
                    logErr("Additional error occurred when tried to append error tag to post")
                    e.printStackTrace()
                }
            }
        }
    }

    private fun updatePostTags(post: Szurubooru.Post, postsToBeNoted: ArrayList<PostSet>, createdTags: HashSet<TagSet>, newPostsOnly: Boolean) {
        if (post.isImage() == false) {
            logErr("Post ${post.id} is not an image.")
            replacePostTriggerTag(post, config.errorTag)
            return
        }

        val sourceImageUrl: String?
        if (newPostsOnly || config.storeSourceUrl == false || post.source.contains(Danbooru.URL_BASE) == false) {
            sourceImageUrl = searchPostOnIqdb(post)
        } else {
            log("Using stored source URL for post ${post.id}: ${post.source}")
            sourceImageUrl = post.source
        }
        sourceImageUrl ?: return

        val danPost = danbooru.getPost(sourceImageUrl)
        if (config.updateImageRating) {
            szurubooru.updatePostSafety(post, danPost.rating.toSzurubooruSafety())
            log("Updated post ${post.id} safety to ${danPost.rating}")
        }
        if (config.updateImageNotes && danPost.hasNotes) {
            postsToBeNoted.add(PostSet(post, danPost))
        }

        val newPostTags = toSzuruTagsMap(danPost.tags)
        newPostTags.forEach {
            if (szuruTags.contains(it.szuruTag) == false) createdTags.add(it)
        }

        szurubooru.updatePostTags(post, *toSzuruTags(danPost.tags).plus(config.managedTag).toTypedArray())

        if (config.createCommentWhenBiggerImageFound && newPostsOnly) {
            if (danPost.width > post.width || danPost.height > post.height) {
                szurubooru.createPostComment(post, "Bigger version of this image was found on [Danbooru](${danPost.getUrl()}).")
                log("Found bigger version of post ${post.id}, created comment.")
            }
        }
    }

    private fun searchPostOnIqdb(post: Szurubooru.Post): String? {
        log("Searching IQDB match for post ${post.id}...")
        val sourceImageUrl = szurubooru.searchPostOnIqdb(post)

        if (sourceImageUrl == null) {
            log("Post ${post.id} not found in the IQDB datebase.")
            replacePostTriggerTag(post, config.noMatchTag)
        } else {
            log("Found post ${post.id} match: $sourceImageUrl")
            if (config.storeSourceUrl) {
                szurubooru.updatePostSource(post, sourceImageUrl)
                log("Updated post ${post.id} source")
            }
        }

        return sourceImageUrl
    }

    private fun updatePostsNotes(postsToBeNoted: ArrayList<PostSet>) {
        if (config.updateImageNotes) {
            log("There are ${postsToBeNoted.size} posts that will have notes updated.")
            postsToBeNoted.forEachIndexed { i, postSet ->
                try {
                    updatePostNote(postSet)
                    log("Updated post ${postSet.szuruPost.id} notes. Completed ${i + 1}/${postsToBeNoted.size}.")
                } catch(e: Exception) {
                    logErr("Error occurred while updating post ${postSet.szuruPost.id} notes")
                    e.printStackTrace()
                }
            }
        }
    }

    private fun updatePostNote(postSet: PostSet) {
        val danPost = postSet.danPost
        val szuruPost = postSet.szuruPost
        val danNotes = danbooru.getPostNotes(postSet.danPost.id)
        val szuruNotes = ArrayList<JsonObject>()
        danNotes.forEach { danNote ->
            if (danNote.active == false) return@forEach

            val noteX = danNote.x.toFloat() / danPost.width
            val noteY = danNote.y.toFloat() / danPost.height
            val noteWidth = danNote.width.toFloat() / danPost.width
            val noteHeight = danNote.height.toFloat() / danPost.height
            val note = jsonObject(
                    "polygon" to jsonArray(
                            jsonArray(noteX, noteY),
                            jsonArray(noteX + noteWidth, noteY),
                            jsonArray(noteX + noteWidth, noteY + noteHeight),
                            jsonArray(noteX, noteY + noteHeight)
                    ),
                    "text" to htmlToMarkdown(danNote.body)
            )
            szuruNotes.add(note)
        }
        szurubooru.updatePostNotes(szuruPost, szuruNotes)
    }

    private fun updateTags(createdTags: HashSet<TagSet>) {
        log("There are ${createdTags.size} new tags that needs to be updated")
        createdTags.forEachIndexed { i, tag ->
            try {
                updateTag(tag)
                log("Updated tag ${tag.danTag}. Completed ${i + 1}/${createdTags.size}.")
            } catch(e: Exception) {
                logErr("Error occurred while updating tag ${tag.danTag}")
                e.printStackTrace()
            }
        }
    }

    private fun updateTag(tag: TagSet) {
        val danTag = danbooru.getTag(tag.danTag)
        szurubooru.updateTag(tag.szuruTag, remapTagCategory(danTag.category.remapName),
                if (config.tags.obtainAliases) toSzuruTags(danTag.aliases) else emptyList<String>(),
                if (config.tags.obtainImplications) toSzuruTags(danTag.implications) else emptyList<String>(),
                if (config.tags.obtainSuggestions) toSzuruTags(danTag.relatedTags) else emptyList<String>())
    }

    private fun toSzuruTags(tags: List<String>): List<String> {
        return toSzuruTagsMap(tags).map { it.szuruTag }
    }

    private fun toSzuruTagsMap(tags: List<String>): List<TagSet> {
        return tags
                .filterNot { config.tags.ignoreTags.contains(it) }
                .filterNot { it.startsWith("/") }
                .map { TagSet(remapTag(it), it) }
                .filter {
                    val matches = tagNameRegex.matches(it.szuruTag)
                    if (matches == false) log("Removing invalid tag \"${it.szuruTag}\" (did not match server tag name regex)")
                    matches
                }
    }

    private fun remapTagCategory(category: String): String {
        config.tags.remapCategories.forEach { remap: RemapDto ->
            if (remap.from.equals(category)) return remap.to
        }

        return category
    }

    private fun replacePostTriggerTag(post: Szurubooru.Post, newTag: String) {
        val newTagsList = post.tags.minus(config.triggerTag).plus(newTag)
        szurubooru.updatePostTags(post, *newTagsList.toTypedArray())
    }

    fun dispose() {
        if (config.singleInstance.enabled) {
            lockSocket.close()
        }
    }

    private fun checkIfRunning() {
        try {
            lockSocket = ServerSocket(config.singleInstance.port, 0, InetAddress.getByAddress(byteArrayOf(127, 0, 0, 1)))
        } catch (e: BindException) {
            logErr("Another instance is already running or port ${config.singleInstance.port} is being used by other application.")
            exitProcess(1)
        } catch (e: IOException) {
            e.printStackTrace()
            exitProcess(2)
        }
    }

    private fun remapTag(danbooruTag: String): String {
        return tagMap.getOrPut(danbooruTag, { escapeTagName(danbooruTag) })
    }

    private fun reverseTagRemap(szurubooruTag: String): TagSet {
        for ((danTag, szuruTag) in tagMap) {
            if (szuruTag == szurubooruTag) return TagSet(szuruTag, danTag)
        }
        return TagSet(szurubooruTag, szurubooruTag) //Fallback
    }

    private fun htmlToMarkdown(html: String): String {
        var escaped = remark.convert(html)
        //since Szurubooru can't handle escaped Markdown characters we need to manually replace them with proper HTML entities
        arrayOf("*" to "&#42;",
                "_" to "&#95;",
                "{" to "&#123;",
                "}" to "&#125;",
                "[" to "&#91;",
                "]" to "&#93;",
                "(" to "&#40;",
                ")" to "&#41;",
                "#" to "&#35;",
                "+" to "&#43;",
                "-" to "&#45;",
                "." to "&#46;",
                "!" to "&#33;",
                "\\" to "&#92;",
                "`" to "&#96;")
                .forEach { escaped = escaped.replace("\\" + it.first, it.second) }
        return escaped.replace("<tn>", "\n\n*").replace("<tn/>", "*").replace("</tn>", "*")
    }

    private fun escapeTagName(danbooruTag: String): String {
        val tagEscaping = config.tags.tagEscaping
        var escapedName = danbooruTag
        tagEscaping.escapeCharacters.forEach { escapedName = escapedName.replace(it.toString(), tagEscaping.escapeWith) }
        return escapedName
    }

    fun File.child(path: String): File {
        return File(this, path)
    }

    data class TagSet(val szuruTag: String, val danTag: String)
    data class PostSet(val szuruPost: Szurubooru.Post, val danPost: Danbooru.Post)

    enum class Task(val description: String, val hasArgument: Boolean = false) {
        NewPosts("Updates new posts (having config.triggerTag)"),
        ExistingPosts("Updates already tagged posts (having config.managedTag)"),
        NewTags("Updates tags that weren't ever updated"),
        ExistingTags("Updates existing tags"),
        Posts("Updates specified posts, you must specify post ids: Posts <postId1> [postId2] [postId3] ...", true),
        Tags("Updates specified tags, you must specify tag names: Tags <tagName1> [tagName2] [tagName3] ...", true),
        Notes("Updates specified post notes only, you must specify post ids: Notes <postId1> [postId2] [postId3] ...", true),
        BatchUpload("Upload all image files from given directory. You must specify path to source directory: BatchUpload <path>. " +
                "Warning: Uploaded images will be moved to 'uploaded' subdirectory to simplify upload resuming.", true),
        BatchDownload("Download all images matching search query. You must specify query and optionally output directory: BatchDownload <searchQuery> [outputPath]. ", true)
    }
}
