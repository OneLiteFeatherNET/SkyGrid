# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

# SkyGrid-ng

A Paper 26.1 chunk generator plugin in pure Java 25 with a hexagonal architecture. It ships two
generation algorithms (`skygrid`, `noise_skygrid`), a YAML config, an in game `/skygrid`
admin command, and i18n. Maintained by OneLiteFeather (OLF).

This file is the always on context for every session. The deeper, task specific knowledge lives in
the skills under `.claude/skills/`. Read the matching skill before doing the work.

## Standard workflow

Every prompt in this repository, without exception for size or triviality, runs through the nine
phase chain-of-thought in `.claude/skills/standard-workflow/SKILL.md`: check spelling/clarity,
derive requirements, get requirements approved, survey the current repo state, research external
sources (web, DeepWiki, Context7), draft an implementation plan, get the plan approved, implement
with feedback folded in, test (deciding per change whether ArchUnit coverage is warranted), and
close with a result summary. Read that skill before responding to any prompt, not just large ones.
Delegated work (investigation, research, implementation, tests) always goes through the `producer`
agent first; it is the standard entry point for the specialist team, not a fallback.

## Golden rules

- English everywhere in the repository: code, identifiers, comments, docs, and commit messages.
- Never use em dashes anywhere (not in code, docs, or commits). Use a comma, colon, parentheses,
  or a period instead.
- The `domain` module never imports Bukkit, Jackson, Guice, jakarta.inject, Hibernate or Jakarta
  Persistence; its compile classpath enforces this. Adventure (`net.kyori.adventure`) is allowed in the
  domain as a platform-neutral text API (it is `compileOnly`; the platform provides it at runtime), but
  its platform-specific bindings (`net.kyori.adventure.platform`) are not. If an edit would break this,
  the design is wrong. See the `hexagonal-architecture` skill.
- Keep value types immutable (records), validate in compact constructors, and keep generation
  deterministic from the world seed.
- The domain is platform-neutral, not just Bukkit-import-free: name blocks by namespaced `BlockId`
  (`minecraft:stone`), never a Bukkit `Material` enum name, in the domain or the config files.
  Resolve to a native block only in the plugin module (WorldEdit pattern; see `hexagonal-architecture`).

## Architecture map

Two Gradle modules hold the production code, and `verification/` holds the ArchUnit rules. The code is
packaged by feature, and the same feature package name exists in both modules (split packages across the
two jars are fine, there is no JPMS):

- `domain/` (framework free): the generation logic, the domain records and the public read-only
  `SkyGridApi`. Packages `generator`, `challenge`, `season`.
- `plugin/` (Paper): the Bukkit, Guice, Jackson and JPA code, in the same feature packages plus
  `persistence`, `command` and `config`. Packages `generator`, `challenge`, `season`, `persistence`,
  `command`, `config`.
- root package `net.onelitefeather.skygridng` (plugin module): `SkyGridNgPlugin`, `I18n`, the shared
  Guice modules and the OLF feature wiring (`OlfFeature`, `OlfModule`). Keep it small.

Feature packages:

- `generator`: terrain algorithms, presets, rules, block and biome ids, decorations, spawn search, the
  generator configuration, the `/skygrid preset|algorithm|maintenance` commands, the chunk generator and
  populator. `SkyGridApi` lives here too.
- `challenge`: challenges, tasks, tiers, challenge and task progress with their JPA entities and
  repositories, challenge behaviors, the challenge sidebar and the challenge commands.
- `season`: seasons, season runs, timeline, transition, ticker, announcer, join listener, world rotation,
  the season repository and entity, the season commands.
- `persistence`: shared database plumbing (`Database`, `DatabaseConfig`, the database warm-up gate).
- `command`: shared command plumbing (Cloud wiring, `CommandSource`, the help command, `Messages`).
- `config`: shared Jackson YAML loading (DTOs, the file repositories).

Dependency rules:

- `domain` never imports Bukkit, Jackson, Guice, jakarta.inject, Hibernate or Jakarta Persistence. The
  module's compile classpath enforces this, and `ArchitectureTest.domainIsFrameworkFree` checks it.
  Adventure (`net.kyori.adventure`) is allowed as a platform-neutral text API; its platform bindings
  (`net.kyori.adventure.platform`) are not.
- Features may use each other directly (inside the one plugin); the hard boundary is between `domain`
  and `plugin`.
- `@Entity` classes live in the plugin module only.

Domain classes carry no injection annotations; swapping an implementation changes a module binding, not
the domain. `SkyGridNgPlugin` is extensible via `modules()`, `injector()`, and
`onExtensionEnable()`/`onExtensionDisable()`. Shared build config lives in the
`skygrid.java-conventions` plugin in `buildSrc`. The architecture rules are enforced by ArchUnit in
`:verification`.

Plugin lifecycle: `onLoad` builds the whole Guice graph eagerly (`Stage.PRODUCTION`, which loads
config), `onEnable` registers i18n, the `SkyGridApi` Bukkit service, and the command;
`getDefaultWorldGenerator` hands Paper the chunk generator. The plugin must load at server `STARTUP` so
the generator exists before worlds load. The active algorithm is read from `RuleState` and is switchable
at runtime via `/skygrid algorithm <id>`.

## Where to look

- Any prompt at all, before doing anything else: read `.claude/skills/standard-workflow/SKILL.md`.
- Changing or adding Java code (domain logic, feature packages, repositories, command handlers, Guice
  modules): read `.claude/skills/hexagonal-architecture/SKILL.md`.
- Writing or changing tests: read `.claude/skills/testing/SKILL.md`.
- Changing the Gradle build, dependencies, plugin-yml, publishing, CI, or cutting a release:
  read `.claude/skills/build-and-release/SKILL.md`.
- Working on what the plugin generates or its config and gameplay behavior:
  read `.claude/skills/skygrid-domain/SKILL.md`.
- Editing the config files, presets/ or rules/ layout, the YAML format,
  or the per-block state/physics/placement rules: read `.claude/skills/config-schema-and-rules/SKILL.md`.
- Planning or reviewing a feature against its requirements:
  read `.claude/skills/user-stories/SKILL.md`.
- Needing the why behind a structural, dependency, licensing, naming, or process choice (so you do
  not undo a deliberate decision): read `.claude/skills/project-context/SKILL.md`.

## Common commands

- Build (all modules): `./gradlew build`
- Test: `./gradlew test` (JUnit 5 + AssertJ + Mockito + MockBukkit v4); one module: `./gradlew :domain:test`
- Single test: `./gradlew :domain:test --tests "net.onelitefeather.skygridng.generator.SkyGridAlgorithmsTest"`
  (append `.methodName` to run one method)
- Run a test server: `./gradlew :plugin:runServer` (creates `run/`, which is gitignored)
- Build the shaded jar: `./gradlew :plugin:shadowJar` (output
  `plugin/build/libs/skygrid-ng-<version>.jar`)
- Fast domain check: `.claude/skills/hexagonal-architecture/scripts/compile-core.sh` (javac on the domain module)

## Commits and versioning

Use Conventional Commits (`feat`, `fix`, `refactor`, `test`, `build`, `chore`, `docs`, `ci`).
release-please reads the history to bump the version in `gradle.properties` and write the
changelog. A breaking change uses `!` (for example `refactor!:`). Do not edit the version by hand.

## graphify

This project has a knowledge graph at graphify-out/ with god nodes, community structure, and cross-file relationships.

Rules:
- For codebase questions, first run `graphify query "<question>"` when graphify-out/graph.json exists. Use `graphify path "<A>" "<B>"` for relationships and `graphify explain "<concept>"` for focused concepts. These return a scoped subgraph, usually much smaller than GRAPH_REPORT.md or raw grep output.
- If graphify-out/wiki/index.md exists, use it for broad navigation instead of raw source browsing.
- Read graphify-out/GRAPH_REPORT.md only for broad architecture review or when query/path/explain do not surface enough context.
- After modifying code, run `graphify update .` to keep the graph current (AST-only, no API cost).
