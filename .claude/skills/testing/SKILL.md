---
name: testing
description: >
  Use whenever writing, changing, or reviewing tests for SkyGrid-ng, or when adding code that
  needs coverage. Defines the JUnit 5 + AssertJ + Mockito + MockBukkit v4 stack, the black-box
  (ports and MockBukkit) versus white-box (internal logic) split per layer, the shared fixtures,
  and the rule that every non trivial expected value is verified empirically against the real
  compiled core before being asserted. Read this before adding a test so it matches the suite
  conventions, asserts values that are actually correct, and compiles against the real APIs
  (MockBukkit v4 package names, paper-api on the test classpath, Material under MockBukkit).
---

# Testing SkyGrid-ng

The suite proves the hexagonal payoff: the core is tested without a server, and the adapters are
tested against the real Bukkit edge with MockBukkit. Keep both halves.

## Stack

JUnit 5 (Jupiter) + AssertJ (`assertThat`) + Mockito (`mock`, `when`, `verify`, `ArgumentCaptor`) +
MockBukkit v4. The build already wires these. Two non obvious build facts:

- `paper-api` is `compileOnly` for main, which is NOT inherited by tests, so it is also
  `testImplementation`. Tests can therefore reference Bukkit and Adventure types.
- MockBukkit v4 lives under `org.mockbukkit.mockbukkit` (not the old `be.seeseemelk`). The artifact
  tracks the Paper MC line (`mockbukkit-v26.1.2`). Core classes: `MockBukkit` (`mock()`,
  `load(Class)`, `unmock()`), `ServerMock`, `entity.PlayerMock`.

Shared fixtures live in `domain/src/testFixtures/java/.../testutil/Fixtures.java`: `SEED`,
`defaultAlgorithmFactories()`, `mc(String)`, `skyConfig()`, `config(algorithm)`, `presetCatalog(algorithm)`,
`InMemoryPresetRepository`. Reuse them. (The WFC-era fixtures `tiles()`, `pairs()`, `settings()`,
`ruleSet()` are gone along with the `wfc` algorithm; do not reference them.)

## Black-box vs white-box, by layer

- `core/util`, `core/state`, `core/algorithm`: white-box. Exercise internal logic, branches, and edge
  cases directly (cumulative buckets, value noise, Memento/Observer, grid math, record guards). The
  `core/solver` package (`WfcSolver` and its strategies) was removed along with the `wfc` algorithm.
- `core/service`: black-box against the in port contracts (`TerrainAlgorithms`, `RuleAdminUseCase`,
  and the preset/spawn/challenge ports). Use an in memory `RuleRepository`/`PresetRepository` where
  needed.
- `plugin`: black-box round-trip against the `PresetRepository`/`RuleRepository`/
  `ChallengeRepository` contracts with `@TempDir` (`Jackson*RepositoryTest`).
- `plugin`: MockBukkit for the Material registry, Mockito for `WorldInfo` and
  `ChunkData`, then assert which blocks are placed. `MaterialResolver` is package private, so its
  test lives in the same package.
- `adapters/command/handler`: no MockBukkit, no Mockito `CommandSender`. Call the `@Command`
  annotated method directly against a `CommandSource` test double (`RecordingCommandSource`, which
  records every sent `Component`); Cloud's own argument parsing and permission gate are not part of
  what these tests exercise. Do not assert exact translated text, just delegation and which
  `Messages.*` method fired.
- `bootstrap`: MockBukkit integration. Load the plugin, assert it enables and the default world
  generator is the hexagonal one. The Cloud command manager's construction is guarded and throws
  under MockBukkit (see `SkyGridNgPlugin`), so do not assert on `cloudCommandManager` there; assert
  the config files are seeded instead (`config.<ext>`, `presets/`, `rules/` exist in the data
  folder).

## The verification rule (important)

Do not invent expected numbers. Before asserting any non trivial value (a distribution, a bitmask,
an entropy, a block count, a surface material), verify it empirically against the real compiled core
with a throwaway plain Java driver, then assert exactly that value. The core compiles standalone
(see the `hexagonal-architecture` skill `compile-core.sh`), so a driver compiled against it gives
ground truth without Gradle or a network. `references/patterns.md` shows the workflow and lists the
values already verified.

## What not to do

- Do not assert exact text of translatable command messages; MockBukkit may not render them.
- Do not use `allMatch(predicate)` on a primitive `int[]` AssertJ assertion (it does not exist); use
  a loop or convert to a stream.
- Do not construct `HexagonalChunkGenerator` outside MockBukkit; its field initializer touches
  `Material.STONE`, which needs the registry.
- Do not mock final `JavaPlugin` methods with Mockito; for graph wiring tests use a MockBukkit loaded
  plugin as the platform instead.

Concrete snippets, imports, and the verified value table are in `references/patterns.md`.
