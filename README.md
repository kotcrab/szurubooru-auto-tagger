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
4. On Szurubooru create tag categories: `general`, `artist`, `character`, `copyright`. Note: you can remap any category or tag in config file.
4. Create `config.yaml` file. Either override what you need from `config.default.yaml` or copy that file and modify it.
5. Backup your stuff and run auto-tagger: `java -jar auto-tagger.jar /path/to/config/file.yaml`

#### Developing

```
./gradlew run # run from sources
./gradlew jar # create fat jar under build/libs
./gradlew zip # create dist zip under build/distributions
```
