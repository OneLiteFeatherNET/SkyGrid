# SkyGrid-ng

A Paper 26.1 chunk generator plugin in pure Java 25 with a hexagonal architecture. It ships three
generation algorithms (`skygrid`, `noise_skygrid`, `wfc`), a JSON config, an in game `/skygrid`
admin command, and i18n. Maintained by OneLiteFeather (OLF).

This file is the always on context for every session. The deeper, task specific knowledge lives in
the skills under `.claude/skills/`. Read the matching skill before doing the work.

## Golden rules

- English everywhere in the repository: code, identifiers, comments, docs, and commit messages.
- Never use em dashes anywhere (not in code, docs, or commits). Use a comma, colon, parentheses,
  or a period instead.
- The dependency direction is one way: `adapter -> port -> core`. The `core` package must never
  import Bukkit, Gson, Guice, jakarta.inject, or Adventure. If an edit would add such an import to
  `core`, the design is wrong. See the `hexagonal-architecture` skill.
- Keep value types immutable (records), validate in compact constructors, and keep generation
  deterministic from the world seed.

## Where to look

- Changing or adding Java code (core, ports, adapters, services, generator, command, repository,
  Guice modules): read `.claude/skills/hexagonal-architecture/SKILL.md`.
- Writing or changing tests: read `.claude/skills/testing/SKILL.md`.
- Changing the Gradle build, dependencies, plugin-yml, publishing, CI, or cutting a release:
  read `.claude/skills/build-and-release/SKILL.md`.

## Common commands

- Build: `./gradlew build`
- Test: `./gradlew test` (JUnit 5 + AssertJ + Mockito + MockBukkit v4)
- Run a test server: `./gradlew runServer` (creates `run/`, which is gitignored)
- Build the shaded jar: `./gradlew shadowJar` (output `build/libs/skygrid-ng-<version>.jar`)

## Commits and versioning

Use Conventional Commits (`feat`, `fix`, `refactor`, `test`, `build`, `chore`, `docs`, `ci`).
release-please reads the history to bump the version in `gradle.properties` and write the
changelog. A breaking change uses `!` (for example `refactor!:`). Do not edit the version by hand.
