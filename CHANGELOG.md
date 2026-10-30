# Changelog

## [0.3.0](https://github.com/OneLiteFeatherNET/SkyGrid/compare/v0.2.3...v0.3.0) (2026-10-30)


### ⚠ BREAKING CHANGES

* **config:** Only YAML config files are read; JSON and TOML files and the generated schema folder are no longer supported.

### Bug Fixes

* **deps:** update dependency org.junit.jupiter:junit-jupiter to v5.13.3 ([#89](https://github.com/OneLiteFeatherNET/SkyGrid/issues/89)) ([5357c27](https://github.com/OneLiteFeatherNET/SkyGrid/commit/5357c27c8cb7c09307abcd41afec7a0314b1729b))
* **deps:** update dependency org.junit.jupiter:junit-jupiter to v5.13.4 ([#92](https://github.com/OneLiteFeatherNET/SkyGrid/issues/92)) ([ab5f554](https://github.com/OneLiteFeatherNET/SkyGrid/commit/ab5f5547f4597fd0f56756d8952b598af1da7b18))
* **deps:** update dependency org.junit.jupiter:junit-jupiter to v5.14.0 ([#98](https://github.com/OneLiteFeatherNET/SkyGrid/issues/98)) ([b1c7b1b](https://github.com/OneLiteFeatherNET/SkyGrid/commit/b1c7b1b7114dd253c416fe088a2bb930da8cf003))
* **deps:** update dependency org.junit.platform:junit-platform-launcher to v1.13.2 ([#90](https://github.com/OneLiteFeatherNET/SkyGrid/issues/90)) ([c41db4f](https://github.com/OneLiteFeatherNET/SkyGrid/commit/c41db4f38bba166a1460284df557f66cb064bc60))
* **deps:** update dependency org.junit.platform:junit-platform-launcher to v1.13.3 ([#95](https://github.com/OneLiteFeatherNET/SkyGrid/issues/95)) ([fae0354](https://github.com/OneLiteFeatherNET/SkyGrid/commit/fae0354f9a2ae8b80ab757006dc96a470dbecb8b))
* **deps:** update dependency org.junit.platform:junit-platform-launcher to v1.13.4 ([#96](https://github.com/OneLiteFeatherNET/SkyGrid/issues/96)) ([690e8bc](https://github.com/OneLiteFeatherNET/SkyGrid/commit/690e8bcdf7ef3b1237ef5337d92cc7dd84c1a12e))
* **deps:** update dependency org.mockito:mockito-core to v5.16.1 ([#85](https://github.com/OneLiteFeatherNET/SkyGrid/issues/85)) ([7a5e9de](https://github.com/OneLiteFeatherNET/SkyGrid/commit/7a5e9def36e808653804c05ee748ada86f453a8d))
* **deps:** update dependency org.postgresql:postgresql to v42.7.14 ([#84](https://github.com/OneLiteFeatherNET/SkyGrid/issues/84)) ([92c236d](https://github.com/OneLiteFeatherNET/SkyGrid/commit/92c236dfd5ce382fc54f4d35d21a0a13104549d4))
* **deps:** update flyway to v11.10.0 ([#86](https://github.com/OneLiteFeatherNET/SkyGrid/issues/86)) ([18f427e](https://github.com/OneLiteFeatherNET/SkyGrid/commit/18f427e3137ac9c9f6413531e6268e5c13e4bc96))
* **deps:** update flyway to v11.10.1 ([#94](https://github.com/OneLiteFeatherNET/SkyGrid/issues/94)) ([0f71c52](https://github.com/OneLiteFeatherNET/SkyGrid/commit/0f71c523c005d89449ac458ed1fb4f6e0372d477))
* **deps:** update flyway to v11.10.2 ([#97](https://github.com/OneLiteFeatherNET/SkyGrid/issues/97)) ([c557d33](https://github.com/OneLiteFeatherNET/SkyGrid/commit/c557d33bd8b63cf8d9830f12f9cc83870bebc92a))
* **deps:** update flyway to v11.10.3 ([#100](https://github.com/OneLiteFeatherNET/SkyGrid/issues/100)) ([61321e2](https://github.com/OneLiteFeatherNET/SkyGrid/commit/61321e25e171d0159910e14bd1c309cb5e7f55a3))


### Code Refactoring

* **config:** read only YAML config files ([#104](https://github.com/OneLiteFeatherNET/SkyGrid/issues/104)) ([f6c60f6](https://github.com/OneLiteFeatherNET/SkyGrid/commit/f6c60f680429a37a2777c1a8694922ff625649c1))

## [0.2.3](https://github.com/OneLiteFeatherNET/SkyGrid/compare/v0.2.2...v0.2.3) (2026-10-29)


### Bug Fixes

* **deps:** update dependency jakarta.persistence:jakarta.persistence-api to v3.2.0 ([#20](https://github.com/OneLiteFeatherNET/SkyGrid/issues/20)) ([54d5404](https://github.com/OneLiteFeatherNET/SkyGrid/commit/54d5404744dffec205d18f5644c4bd6901c20049))
* **deps:** update dependency org.junit.jupiter:junit-jupiter to v5.13.2 ([#81](https://github.com/OneLiteFeatherNET/SkyGrid/issues/81)) ([1e986a9](https://github.com/OneLiteFeatherNET/SkyGrid/commit/1e986a93cd52ea5514c9e26ebab0c84a69a668c3))
* **deps:** update dependency org.junit.platform:junit-platform-launcher to v1.13.1 ([#82](https://github.com/OneLiteFeatherNET/SkyGrid/issues/82)) ([5bba08b](https://github.com/OneLiteFeatherNET/SkyGrid/commit/5bba08bb8bdb86b864fce135abceb3d3b429d810))
* **deps:** update dependency org.mockito:mockito-core to v5.16.0 ([#58](https://github.com/OneLiteFeatherNET/SkyGrid/issues/58)) ([e820376](https://github.com/OneLiteFeatherNET/SkyGrid/commit/e820376bf3305c9943529948233186dbb90c16f8))
* **deps:** update dependency org.postgresql:postgresql to v42.7.13 ([#78](https://github.com/OneLiteFeatherNET/SkyGrid/issues/78)) ([8de42f7](https://github.com/OneLiteFeatherNET/SkyGrid/commit/8de42f73f025bd215e497e1a078168bebdd2decf))
* **persistence:** run the legacy history check through the flyway data source ([#88](https://github.com/OneLiteFeatherNET/SkyGrid/issues/88)) ([4bb8f96](https://github.com/OneLiteFeatherNET/SkyGrid/commit/4bb8f96464053ebf60bb0cc0070c4bf258a358b9))

## [0.2.2](https://github.com/OneLiteFeatherNET/SkyGrid/compare/v0.2.1...v0.2.2) (2026-10-29)


### Bug Fixes

* **deps:** update dependency org.junit.jupiter:junit-jupiter to v5.13.0 ([#72](https://github.com/OneLiteFeatherNET/SkyGrid/issues/72)) ([ef5d3c7](https://github.com/OneLiteFeatherNET/SkyGrid/commit/ef5d3c76de3edc1c84020e8b9500fa9114f61f57))
* **deps:** update dependency org.junit.jupiter:junit-jupiter to v5.13.1 ([#80](https://github.com/OneLiteFeatherNET/SkyGrid/issues/80)) ([2aa70a3](https://github.com/OneLiteFeatherNET/SkyGrid/commit/2aa70a35e76656073081d146861b94248d6d2c93))
* **deps:** update dependency org.junit.platform:junit-platform-launcher to v1.13.0 ([#73](https://github.com/OneLiteFeatherNET/SkyGrid/issues/73)) ([e0b1384](https://github.com/OneLiteFeatherNET/SkyGrid/commit/e0b1384d779d88b5c52b08585a17436def74b0ad))
* **deps:** update dependency org.postgresql:postgresql to v42.7.11 ([#70](https://github.com/OneLiteFeatherNET/SkyGrid/issues/70)) ([f63f5ae](https://github.com/OneLiteFeatherNET/SkyGrid/commit/f63f5ae923c4a878475df833e0483fa812d8d4cc))
* **deps:** update dependency org.postgresql:postgresql to v42.7.12 ([#75](https://github.com/OneLiteFeatherNET/SkyGrid/issues/75)) ([b766489](https://github.com/OneLiteFeatherNET/SkyGrid/commit/b7664896561d6710c5f6cdc81674b32c6256c846))
* **deps:** update flyway to v11.9.2 ([#68](https://github.com/OneLiteFeatherNET/SkyGrid/issues/68)) ([512d039](https://github.com/OneLiteFeatherNET/SkyGrid/commit/512d039d8d227589a09f9415ed4ace006ef061b2))
* **deps:** update hibernate to v6.6.58.final ([#62](https://github.com/OneLiteFeatherNET/SkyGrid/issues/62)) ([d71bcfc](https://github.com/OneLiteFeatherNET/SkyGrid/commit/d71bcfca6b4dae5afdeb48b79300101ef3860d2b))
* **deps:** update hibernate to v6.6.59.final ([#76](https://github.com/OneLiteFeatherNET/SkyGrid/issues/76)) ([24d7c4f](https://github.com/OneLiteFeatherNET/SkyGrid/commit/24d7c4fe37e98201521a642d4a71e1e4cbcc5796))

## [0.2.1](https://github.com/OneLiteFeatherNET/SkyGrid/compare/v0.2.0...v0.2.1) (2026-10-29)


### Bug Fixes

* **deps:** update dependency com.tngtech.archunit:archunit-junit5 to v1.5.0 ([#19](https://github.com/OneLiteFeatherNET/SkyGrid/issues/19)) ([ea21125](https://github.com/OneLiteFeatherNET/SkyGrid/commit/ea21125aa646497f21ac151a9b5f9397c693ccc1))
* **deps:** update dependency com.tngtech.archunit:archunit-junit5 to v1.5.1 ([#55](https://github.com/OneLiteFeatherNET/SkyGrid/issues/55)) ([2dcba08](https://github.com/OneLiteFeatherNET/SkyGrid/commit/2dcba08d0c64f064a05f2aebf7315f6181b33d3e))
* **deps:** update dependency net.kyori:adventure-api to v4.26.1 ([#45](https://github.com/OneLiteFeatherNET/SkyGrid/issues/45)) ([f670d3c](https://github.com/OneLiteFeatherNET/SkyGrid/commit/f670d3c69645510b564e8e8775d381387c239dc5))
* **deps:** update dependency org.incendo:cloud-paper to v2.0.1 ([#40](https://github.com/OneLiteFeatherNET/SkyGrid/issues/40)) ([f99386a](https://github.com/OneLiteFeatherNET/SkyGrid/commit/f99386a43f18299ef5c60fc9a2a491c7f8c3d6e2))
* **deps:** update dependency org.junit.jupiter:junit-jupiter to v5.12.0 ([#46](https://github.com/OneLiteFeatherNET/SkyGrid/issues/46)) ([3ac6e65](https://github.com/OneLiteFeatherNET/SkyGrid/commit/3ac6e65da361442089550ab0c707e520369fa317))
* **deps:** update dependency org.junit.jupiter:junit-jupiter to v5.12.1 ([#56](https://github.com/OneLiteFeatherNET/SkyGrid/issues/56)) ([632ee12](https://github.com/OneLiteFeatherNET/SkyGrid/commit/632ee120c119fc1878972121d777a04f29a60046))
* **deps:** update dependency org.junit.jupiter:junit-jupiter to v5.12.2 ([#66](https://github.com/OneLiteFeatherNET/SkyGrid/issues/66)) ([9b0ff80](https://github.com/OneLiteFeatherNET/SkyGrid/commit/9b0ff8017295ce7efe8cf833bed9ebcca78af986))
* **deps:** update dependency org.junit.platform:junit-platform-launcher to v1.12.0 ([#47](https://github.com/OneLiteFeatherNET/SkyGrid/issues/47)) ([aff087b](https://github.com/OneLiteFeatherNET/SkyGrid/commit/aff087b9775239632286d7525dc032dbd33b483b))
* **deps:** update dependency org.junit.platform:junit-platform-launcher to v1.12.1 ([#57](https://github.com/OneLiteFeatherNET/SkyGrid/issues/57)) ([49fb832](https://github.com/OneLiteFeatherNET/SkyGrid/commit/49fb832f7a04dc88d1d745bd4d0698becb432dc0))
* **deps:** update dependency org.junit.platform:junit-platform-launcher to v1.12.2 ([#67](https://github.com/OneLiteFeatherNET/SkyGrid/issues/67)) ([7a99cca](https://github.com/OneLiteFeatherNET/SkyGrid/commit/7a99cca86d4ff35e826fe280eb852d8051a0d699))
* **deps:** update dependency org.mockito:mockito-core to v5.15.2 ([#23](https://github.com/OneLiteFeatherNET/SkyGrid/issues/23)) ([e253dae](https://github.com/OneLiteFeatherNET/SkyGrid/commit/e253daee9fde8271c9d041b2df0d2ca018756e58))
* **deps:** update dependency org.postgresql:postgresql to v42.7.10 ([#61](https://github.com/OneLiteFeatherNET/SkyGrid/issues/61)) ([124f451](https://github.com/OneLiteFeatherNET/SkyGrid/commit/124f451335fa72a562e7dd670e9c7728bbe6bed8))
* **deps:** update dependency org.postgresql:postgresql to v42.7.8 ([#10](https://github.com/OneLiteFeatherNET/SkyGrid/issues/10)) ([559780b](https://github.com/OneLiteFeatherNET/SkyGrid/commit/559780be7e8010effc1618824d2ccfb6c709cbe1))
* **deps:** update dependency org.postgresql:postgresql to v42.7.9 ([#52](https://github.com/OneLiteFeatherNET/SkyGrid/issues/52)) ([f4ffa83](https://github.com/OneLiteFeatherNET/SkyGrid/commit/f4ffa8383a23d0c4bd5eb2759f8527e003fb26a7))
* **deps:** update flyway to v11.9.0 ([#24](https://github.com/OneLiteFeatherNET/SkyGrid/issues/24)) ([5d2204c](https://github.com/OneLiteFeatherNET/SkyGrid/commit/5d2204c7d5d997322edf654ef5c392702e95600f))
* **deps:** update flyway to v11.9.1 ([#59](https://github.com/OneLiteFeatherNET/SkyGrid/issues/59)) ([d0cbf03](https://github.com/OneLiteFeatherNET/SkyGrid/commit/d0cbf03021f2da6c660daa44d8278251136f78d4))
* **deps:** update hibernate to v6.6.56.final ([#41](https://github.com/OneLiteFeatherNET/SkyGrid/issues/41)) ([87d707e](https://github.com/OneLiteFeatherNET/SkyGrid/commit/87d707e5902a3da2bdfa727723115191990032a2))
* **deps:** update hibernate to v6.6.57.final ([#53](https://github.com/OneLiteFeatherNET/SkyGrid/issues/53)) ([e6e3d08](https://github.com/OneLiteFeatherNET/SkyGrid/commit/e6e3d08fef08d0bb85c3a939de3f3ecedb977536))
* **deps:** update jackson to v2.22.2 ([#42](https://github.com/OneLiteFeatherNET/SkyGrid/issues/42)) ([39fe48c](https://github.com/OneLiteFeatherNET/SkyGrid/commit/39fe48c29e2ba3fdae8ddb6e13d6e077cc495b8e))
* **deps:** update jackson to v2.22.3 ([#50](https://github.com/OneLiteFeatherNET/SkyGrid/issues/50)) ([d89431d](https://github.com/OneLiteFeatherNET/SkyGrid/commit/d89431d8df1a356b7a73a9d938103bcbd9d6c379))
* **deps:** update jackson to v2.22.4 ([#63](https://github.com/OneLiteFeatherNET/SkyGrid/issues/63)) ([08f7e38](https://github.com/OneLiteFeatherNET/SkyGrid/commit/08f7e387cc1503a37510ccc7c8f712aeb265c9b0))
* **seasons:** show the upcoming season before the first season starts ([#48](https://github.com/OneLiteFeatherNET/SkyGrid/issues/48)) ([1e23186](https://github.com/OneLiteFeatherNET/SkyGrid/commit/1e2318652635fe42f2e82d279014af8d327dc4e1))

## [0.2.0](https://github.com/OneLiteFeatherNET/SkyGrid/compare/v0.1.1...v0.2.0) (2026-10-29)


### Features

* **seasons:** rotate season worlds with per-season progress ([#36](https://github.com/OneLiteFeatherNET/SkyGrid/issues/36)) ([2cf55e1](https://github.com/OneLiteFeatherNET/SkyGrid/commit/2cf55e1eac2f3f16ae4348e331401c4b30ef419a))


### Bug Fixes

* **deps:** update cloud to v2.1.0 ([#18](https://github.com/OneLiteFeatherNET/SkyGrid/issues/18)) ([2cca7aa](https://github.com/OneLiteFeatherNET/SkyGrid/commit/2cca7aa87f0db7c2e3a488244563add6434bca7e))
* **deps:** update dependency com.zaxxer:hikaricp to v6.3.1 ([#5](https://github.com/OneLiteFeatherNET/SkyGrid/issues/5)) ([abc8c73](https://github.com/OneLiteFeatherNET/SkyGrid/commit/abc8c734df969ca2e8782186be9b134d52f7d379))
* **deps:** update dependency net.kyori:adventure-api to v4.25.0 ([#22](https://github.com/OneLiteFeatherNET/SkyGrid/issues/22)) ([5742e7f](https://github.com/OneLiteFeatherNET/SkyGrid/commit/5742e7f85b31b25f781db99a9486f54d18d1a526))
* **deps:** update dependency org.incendo:cloud-paper to v2.0.0 ([#6](https://github.com/OneLiteFeatherNET/SkyGrid/issues/6)) ([38bf527](https://github.com/OneLiteFeatherNET/SkyGrid/commit/38bf527dcaa9e75a219fd50e55d95f286f51bafc))
* **deps:** update dependency org.junit.jupiter:junit-jupiter to v5.11.4 ([#7](https://github.com/OneLiteFeatherNET/SkyGrid/issues/7)) ([7e65c69](https://github.com/OneLiteFeatherNET/SkyGrid/commit/7e65c69a721fcef0895ef9352f98aa5951aee958))
* **deps:** update dependency org.junit.platform:junit-platform-launcher to v1.11.4 ([#8](https://github.com/OneLiteFeatherNET/SkyGrid/issues/8)) ([1e8e49b](https://github.com/OneLiteFeatherNET/SkyGrid/commit/1e8e49b7809a88c1bb8b1178d74df26a81de0223))
* **deps:** update hibernate to v6.6.55.final ([#11](https://github.com/OneLiteFeatherNET/SkyGrid/issues/11)) ([a494379](https://github.com/OneLiteFeatherNET/SkyGrid/commit/a494379adcfcc7c4c842fe92ec1b9212f175ba97))
* **deps:** update jackson to v2.22.1 ([#12](https://github.com/OneLiteFeatherNET/SkyGrid/issues/12)) ([c99241c](https://github.com/OneLiteFeatherNET/SkyGrid/commit/c99241c532afbe1fc63c22c160dc64a8b0ca1b31))
* **deps:** update testcontainers to v1.21.4 ([#13](https://github.com/OneLiteFeatherNET/SkyGrid/issues/13)) ([315cefe](https://github.com/OneLiteFeatherNET/SkyGrid/commit/315cefea3900c6ef5629fa3fe735b87cd1af905d))

## [0.1.1](https://github.com/OneLiteFeatherNET/SkyGrid/compare/v0.1.0...v0.1.1) (2026-10-28)


### Bug Fixes

* **persistence:** make the database work inside the plugin jar on paper ([#35](https://github.com/OneLiteFeatherNET/SkyGrid/issues/35)) ([a5d2623](https://github.com/OneLiteFeatherNET/SkyGrid/commit/a5d2623a6930361e61fa2a436b0bf0048ac20246))

## [0.1.0](https://github.com/OneLiteFeatherNET/SkyGrid/compare/v0.1.0...v0.1.0) (2026-10-28)


### ⚠ BREAKING CHANGES

* **persistence:** adapters/challenge-progress now depends on jakarta.persistence-api and Hibernate ORM instead of plain JDBC.
* **command:** /skygrid challenge is always available, there is no build without the challenge system anymore.
* platforms/paper-olf and its skygrid-ng-olf-<version>.jar no longer exist; everything ships in the single skygrid-ng-<version>.jar.
* **build:** wire the trimmed algorithm registry and spawn config into the composition root
* **command:** /skygrid list|info|weight|allow|disallow no longer exist.
* **config:** the preset schema no longer accepts a wfc section; spawn no longer accepts platformY/platformBlock/platformRadius/villager.
* **core:** remove the wfc solver, tile-admin service methods, and platform spawn logic
* **core:** RuleAdminUseCase and GenerateRegionUseCase lose the wfc tile-admin surface.
* **core:** SkyGridApi.tileIds() and SkyGridApi.tile(String) are removed; GeneratorConfig and Preset no longer carry a wfc field.
* the modern preset id no longer exists. Any generator spec using SkyGrid-ng:modern must be updated to SkyGrid-ng:standard.
* replace PlacementRule ground/water fields with a structure pattern
* **config:** parse and validate structure patterns for placement rules
* replace PlacementRule ground/water fields with a structure pattern
* the on-disk configuration format changed. The single generator.yaml/generator.conf is replaced by a config selector plus per-preset files under presets/ and per-block rule files under rules/, in JSON, YAML or TOML. Existing configs must be migrated.
* packages were reorganized (net.onelitefeather.skygridng.{api,ports, core,adapters,platforms}.*) and block fields in the model and generator.json are now namespaced ids (minecraft:stone) of type BlockId instead of Bukkit material names.
* rename project to SkyGrid-ng

### Features

* add interface-ux-designer agent and messaging UX conventions ([6c92a12](https://github.com/OneLiteFeatherNET/SkyGrid/commit/6c92a12520535fd7f4665f05d14bb0310c291a67))
* add interface-ux-designer agent and messaging UX conventions ([9f6dc9b](https://github.com/OneLiteFeatherNET/SkyGrid/commit/9f6dc9bb5e616304fed9a0b8d3d6c2a29ff1b637))
* **api,ports:** add ChallengeTier model and challenge progress ports ([e4d0c49](https://github.com/OneLiteFeatherNET/SkyGrid/commit/e4d0c490d1311e8e79ecd9229c4e9958a0bf4592))
* **api:** add ChallengeRule/ChallengeCatalog model and Challenge ports ([2666bca](https://github.com/OneLiteFeatherNET/SkyGrid/commit/2666bcab3e894ea68383a2e410a20e41974c1bd5))
* **api:** add Task/Reward domain model, extend ChallengeRule with tasks ([d4d0440](https://github.com/OneLiteFeatherNET/SkyGrid/commit/d4d04406bd76d7f850722025c8cfaff4720a775f))
* **bootstrap:** wire composition root and ship default generator config ([effeffa](https://github.com/OneLiteFeatherNET/SkyGrid/commit/effeffaa866e68a98e15963c48370f5d94898172))
* **challenge:** add adapters/challenge module with no_crafting_table listener ([ed62c43](https://github.com/OneLiteFeatherNET/SkyGrid/commit/ed62c43f31bafd2ae9307168bb428c9954220039))
* **challenge:** add level_is_border challenge (display-only warning) ([d02e498](https://github.com/OneLiteFeatherNET/SkyGrid/commit/d02e4983124e3aaba77fafb0ead7a8deaff3ab01))
* **challenge:** add no_natural_regeneration challenge ([506aef2](https://github.com/OneLiteFeatherNET/SkyGrid/commit/506aef2f4e56905af4a0172cc33122db8a5b3d55))
* **challenge:** add one_life challenge (permanent per-run elimination) ([a1b41d7](https://github.com/OneLiteFeatherNET/SkyGrid/commit/a1b41d72639eea1ab6bfcb12d8dee88935d7c0d1))
* **challenge:** grant rewards and complete tasks at each round boundary ([6ce77cd](https://github.com/OneLiteFeatherNET/SkyGrid/commit/6ce77cd0a9b973d0c73d449fa732c9e978866881))
* **challenge:** wire one_life to persist tier progress across rounds ([e1be301](https://github.com/OneLiteFeatherNET/SkyGrid/commit/e1be3012a750c3518a23040c5ffb52119273a83c))
* **command:** add /skygrid challenge list|toggle subcommand ([52db9e4](https://github.com/OneLiteFeatherNET/SkyGrid/commit/52db9e44422a127136290e4cc268c64c7ca1b581))
* **command:** add /skygrid challenge tier check and reset ([71b9bf2](https://github.com/OneLiteFeatherNET/SkyGrid/commit/71b9bf28df9c78908deb384ece7cc18d9b69510a))
* **command:** add actionable feedback and reload confirmation to /skygrid ([739515f](https://github.com/OneLiteFeatherNET/SkyGrid/commit/739515fbe1f00d0205d51198c0172af123df60a1))
* **command:** add runtime /wfc administration command ([4d1578b](https://github.com/OneLiteFeatherNET/SkyGrid/commit/4d1578b1f4fbbacd98f29f589c20c0f2e9b044d2))
* **command:** stage 1 of the Incendo Cloud migration (infrastructure) ([60704cb](https://github.com/OneLiteFeatherNET/SkyGrid/commit/60704cb40d6fd64b9b202d6a2c67cc7ad5e85c27))
* **command:** stage 2 of the Incendo Cloud migration (subcommand handlers) ([5fba000](https://github.com/OneLiteFeatherNET/SkyGrid/commit/5fba000a95260077338a72e25e040d81c8cd9753))
* **config:** add bundled defaults for three more challenge rules ([391ec9a](https://github.com/OneLiteFeatherNET/SkyGrid/commit/391ec9a0f5e9bd0fe04dad511defbd0f1e734fc5))
* **config:** add Jackson-backed challenge repository and schema ([72a855e](https://github.com/OneLiteFeatherNET/SkyGrid/commit/72a855ec4e431c708191350c2b474d5bfed6405a))
* **config:** add tasks/sequential/reward to the challenge config schema ([ab31cd0](https://github.com/OneLiteFeatherNET/SkyGrid/commit/ab31cd0a6749824f2fa237bb94d8c8c63473a5a7))
* **core:** add ChallengeProgressService and expose the catalog change listener ([a0ad9b3](https://github.com/OneLiteFeatherNET/SkyGrid/commit/a0ad9b30287c6cca85336e7ea6d417ee3d2f77d8))
* **core:** add ChallengeState and ChallengeAdminService ([e1a2d57](https://github.com/OneLiteFeatherNET/SkyGrid/commit/e1a2d57972e81b2f50610f7baf2b50a0e857aa97))
* **core:** add generation ports and shared rule state ([2f48e50](https://github.com/OneLiteFeatherNET/SkyGrid/commit/2f48e5011aabaad2818b9c21156906101f942fc4))
* **core:** add hexagonal domain model ([0a69d00](https://github.com/OneLiteFeatherNET/SkyGrid/commit/0a69d00bc1f0d7b5f93a314006d2b159cbc35861))
* **core:** add region generation, algorithm selection, and admin services ([ca1e5a8](https://github.com/OneLiteFeatherNET/SkyGrid/commit/ca1e5a82af51d1c803d1e9f7848276893e5222f8))
* **core:** add seed derivation, weighted table, and value noise ([6daed2b](https://github.com/OneLiteFeatherNET/SkyGrid/commit/6daed2b0b7e0511dac7e45a43ab6e5e155499882))
* **core:** add TaskProgressService ([29e4efb](https://github.com/OneLiteFeatherNET/SkyGrid/commit/29e4efba4fd3891f91c09fe31b180b1d6eab546d))
* **core:** add WFC solver with pluggable entropy and collapse strategies ([1bdc24b](https://github.com/OneLiteFeatherNET/SkyGrid/commit/1bdc24b1ea64a4e809ee9618d4b0dbac49a05e97))
* generation overhaul: per-world presets, Jackson config, per-block rules ([ed11329](https://github.com/OneLiteFeatherNET/SkyGrid/commit/ed11329d9e98a592da1c78315957de0b78255c79))
* **i18n:** add Adventure translation store with English and German bundles ([8024a02](https://github.com/OneLiteFeatherNET/SkyGrid/commit/8024a02c79c887d0052b26cd2fa2ef574f06429d))
* **i18n:** render command messages via MiniMessage instead of MessageFormat ([d209df5](https://github.com/OneLiteFeatherNET/SkyGrid/commit/d209df5abbb1b80ad327874aecb303c68d251c25))
* integrate graphify knowledge graph into Claude Code workflow ([d871a45](https://github.com/OneLiteFeatherNET/SkyGrid/commit/d871a455ea9d183e04e61e3c73c4707f1096d795))
* **json:** add Gson generator configuration repository ([00a6b7e](https://github.com/OneLiteFeatherNET/SkyGrid/commit/00a6b7e8419d0f34f1773f5a45c6f3cd6eb38988))
* **paper-olf:** wire the no_crafting_table challenge into the OLF distribution ([77f147a](https://github.com/OneLiteFeatherNET/SkyGrid/commit/77f147a9a2388e95c70abc2fc433f9267dee0c9a))
* **paper-olf:** wire the three new challenges into the OLF distribution ([b1ccb5a](https://github.com/OneLiteFeatherNET/SkyGrid/commit/b1ccb5a106f6a43afc3bdff1e608b6d8c9e2fad2))
* **paper:** add chunk generator and decoration populator adapters ([24013f4](https://github.com/OneLiteFeatherNET/SkyGrid/commit/24013f4922e6789ade21dff65480a7b8fcf90109))
* **persistence:** add adapters/task-progress for per-player task completion ([15f4af5](https://github.com/OneLiteFeatherNET/SkyGrid/commit/15f4af5b8659a8a1215fc382eeb67a90d4dc4cbe))
* **persistence:** add Postgres-backed challenge progress repository ([e483fd1](https://github.com/OneLiteFeatherNET/SkyGrid/commit/e483fd1519ccb0692f4f3f3176571f44f3aaac51))
* **persistence:** extract a shared adapters/persistence module ([9f8c826](https://github.com/OneLiteFeatherNET/SkyGrid/commit/9f8c8266f8ea9de0a8cc18577173628a85e64948))
* **persistence:** migrate challenge progress to Jakarta Persistence (Hibernate ORM) ([c118dec](https://github.com/OneLiteFeatherNET/SkyGrid/commit/c118dec3196b975d97a57dce9536b1cc9aef0bd6))
* **platforms:** wire TaskProgressUseCase, RewardGrantor, and the sidebar feature ([a6d4001](https://github.com/OneLiteFeatherNET/SkyGrid/commit/a6d40019052796b88386095421fc8dcb6fe03cf3))
* **ports:** add TaskProgressUseCase, TaskCompletionRepository, RewardGrantor ([bf69f48](https://github.com/OneLiteFeatherNET/SkyGrid/commit/bf69f487d8ea40689aeae99d3e4b7ea357d07131))
* **scoreboard:** add live per-player task progress sidebar ([b54785a](https://github.com/OneLiteFeatherNET/SkyGrid/commit/b54785a7cf61269718b5adc90471b76b388aa9e4))
* **skygrid:** add independent and noise clustered SkyGrid algorithms ([1705c17](https://github.com/OneLiteFeatherNET/SkyGrid/commit/1705c17bbc83d38d50ec79b2c812cf3db7176705))
* **spawn:** replace the starter platform and villager with a safe-location teleport search ([bad9e5b](https://github.com/OneLiteFeatherNET/SkyGrid/commit/bad9e5bca7b6136d926ff66fd5b7332f88339ec9))


### Bug Fixes

* **build:** set world generator load order and adopt the CycloneDX 3.x API ([6c4da70](https://github.com/OneLiteFeatherNET/SkyGrid/commit/6c4da705df9e2102085e9e5bf85c28e7b1347a39))
* **command:** reformat /skygrid help into a scannable multi-line message ([e6dda29](https://github.com/OneLiteFeatherNET/SkyGrid/commit/e6dda295c741416b4a380f75112f710c728b46b7))
* **i18n:** migrate the German bundle to MiniMessage ([80ab8ad](https://github.com/OneLiteFeatherNET/SkyGrid/commit/80ab8ad4d1f2ff9286c201275a3d7fbf302fbfce))
* **persistence:** bound challenge_id to 64 characters ([e8984d7](https://github.com/OneLiteFeatherNET/SkyGrid/commit/e8984d79b0f4ded18ca66bd7244bad68b0c575cb))
* **persistence:** give each adapter its own flyway location and history table ([#9](https://github.com/OneLiteFeatherNET/SkyGrid/issues/9)) ([d79c324](https://github.com/OneLiteFeatherNET/SkyGrid/commit/d79c324794ffcacc6a485ee7320c7c44aef248bf))


### Code Refactoring

* **build:** wire the trimmed algorithm registry and spawn config into the composition root ([cd7114c](https://github.com/OneLiteFeatherNET/SkyGrid/commit/cd7114ca266368e15412c91fbd059627f493c1ed))
* **command:** challenges are always available now ([aebb356](https://github.com/OneLiteFeatherNET/SkyGrid/commit/aebb3567cb91c1cb496865d7b4799a4517e5c73a))
* **command:** remove the wfc tile-admin subcommands ([23ee54b](https://github.com/OneLiteFeatherNET/SkyGrid/commit/23ee54b4b6fc0c46500bcf1d21498ea691e5247f))
* **config:** drop the wfc config schema and redesign the spawn preset section ([983a562](https://github.com/OneLiteFeatherNET/SkyGrid/commit/983a562baf47068e5ffc9bf90588da7a2625f553))
* **config:** parse and validate structure patterns for placement rules ([59e9afd](https://github.com/OneLiteFeatherNET/SkyGrid/commit/59e9afd15f50e0754f19a9c5e88d4d50961691f6))
* **core:** remove the wfc algorithm's model types and starter-platform config fields ([142acfe](https://github.com/OneLiteFeatherNET/SkyGrid/commit/142acfe8c1e1a212448a87fab9d53e779522b220))
* **core:** remove the wfc solver, tile-admin service methods, and platform spawn logic ([1f7a4ae](https://github.com/OneLiteFeatherNET/SkyGrid/commit/1f7a4ae29b74b54c0cea3b67dd654065737fb2ef))
* **core:** trim the algorithm and spawn ports for the wfc/platform removal ([3178775](https://github.com/OneLiteFeatherNET/SkyGrid/commit/317877525b175386261031ca0bb53560a6f1b8f8))
* fold platforms/paper-olf into platforms/paper ([3c998d2](https://github.com/OneLiteFeatherNET/SkyGrid/commit/3c998d20fc4ff84f99026483907d5975f178d2af))
* rename modern preset to standard and add golden dandelion ([5044706](https://github.com/OneLiteFeatherNET/SkyGrid/commit/5044706b648f73d496c958255ee843375d35b248))
* rename project to SkyGrid-ng ([0dcfa50](https://github.com/OneLiteFeatherNET/SkyGrid/commit/0dcfa50b6d959fe1910ca935beefe3831510f802))
* replace PlacementRule ground/water fields with a structure pattern ([c7109ee](https://github.com/OneLiteFeatherNET/SkyGrid/commit/c7109ee01071a6f575fb6caae94a03fa8092b958))
* replace PlacementRule ground/water fields with a structure pattern ([01e67e4](https://github.com/OneLiteFeatherNET/SkyGrid/commit/01e67e4f21096e1c5c8651abd7c15bc8131cacb5))
* restructure into a multi-module, platform-neutral hexagonal architecture ([5443ab4](https://github.com/OneLiteFeatherNET/SkyGrid/commit/5443ab48b072b92a0306936231f166d68c0cf8e0))


### Continuous Integration

* **release:** release plain versions instead of java snapshots ([#31](https://github.com/OneLiteFeatherNET/SkyGrid/issues/31)) ([91b7c8a](https://github.com/OneLiteFeatherNET/SkyGrid/commit/91b7c8a1b883718f1bc41c5b08780da29e3e17fe))

## Changelog

All notable changes to SkyGrid-ng are documented in this file. This file is maintained by
release-please based on Conventional Commit messages.
