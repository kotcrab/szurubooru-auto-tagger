# szurubooru-auto-tagger

IQDB and Danbooru based auto tagger for [szurubooru2](https://github.com/rr-/szurubooru).

#### How this works
Auto tagger searches for all posts having trigger tag (`auto_tagme` by default), then sends post images to IQDB in order
to find matching image on Danbooru. When match is found, Danbooru tags, post safety and notes are retrieved and set for Szurubooru post.
New tag is added to mark post as being managed by auto tagger (`auto_tagged` by default). Later auto tagger may update
tags on such posts. After posts tagging is finished, new tags that were created are collected and their data is updated
from Danbooru tags. Auto tagger will update tag category but it can also obtain tag aliases, implications and suggestions.
In case when no match is found, post are tagged with `tagme` so you can tag them manually. If you want you can also setup
tag and tag category remapping.
 
#### Usage

1. Make sure you have Java 8 installed. 
2. Get jar from [Releases](https://github.com/kotcrab/szurubooru-auto-tagger/releases) page or build it yourself.
3. Create separate account for auto-tagger and give it high enough privileges.
4. On Szurubooru create tag categories: `general`, `artist`, `character`, `copyright`, `meta`. Note: you can remap categories in config file.
Default category should be set to `general` or your custom one.
4. Create `config.yaml` file. Either override what you need from `config.default.yaml` or copy that file and modify it.
5. Backup your stuff and run auto-tagger: `java -jar auto-tagger.jar --config /path/to/config/file.yaml`

By default only new posts will be tagged, you can specify different task using `--task` switch. Use `--help` to see list of tasks.
Except standard tasks used for post tagging, you can also use `BatchUpload` to quickly upload all images from directory.
Note that uploaded images will be moved to `uploaded` subdirectory to simplify upload resuming.

#### Help message
Result of `java -jar auto-tagger.jar --help`
```
Szurubooru auto tagger. Usage: [-c config-file-path] [-t task task-argument1 task-argument2 task-argument3 ...]
config-file-path: (-c, --config) Path to configuration file. If not specified config.yaml is used or config.default.yaml if former does not exist.
task: (-t, --task) Optional, task that auto tagger will perform. By default 'NewPosts' is used. This parameter can be: 
	NewPosts - Updates new posts (having config.triggerTag)
	ExistingPosts - Updates already tagged posts (having config.managedTag)
	NewTags - Updates tags that weren't ever updated
	ExistingTags - Updates existing tags
	Posts - Updates specified posts, you must specify post ids: Posts <postId1> [postId2] [postId3] ...
	Tags - Updates specified tags, you must specify tag names: Tags <tagName1> [tagName2] [tagName3] ...
	Notes - Updates specified post notes only, you must specify post ids: Notes <postId1> [postId2] [postId3] ...
	BatchUpload - Upload all image files from given directory. You must specify path to source directory: BatchUpload <path>. Warning: Uploaded images will be moved to 'uploaded' subdirectory to simplify upload resuming.
	BatchDownload - Download all images matching search query. You must specify query and optionally output directory: BatchDownload <searchQuery> [outputPath]. 
task-arguments: Optional, only needed if tasks requires passing argument. Must be specified as last config parameter
```

#### Developing

```
./gradlew run # run from sources
./gradlew jar # create fat jar under build/libs
./gradlew zip # create dist zip under build/distributions
```
