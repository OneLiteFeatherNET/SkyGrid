# Changelog

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
