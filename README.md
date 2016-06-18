# szurubooru-auto-tagger

IQDB and Danbooru based auto tagger for [szurubooru2](https://github.com/rr-/szurubooru).

Work in progress.

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
