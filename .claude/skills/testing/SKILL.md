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

Shared fixtures live in `src/test/java/.../testutil/Fixtures.java`: `SEED`, `tiles()`, `pairs()`,
`settings()`, `ruleSet()`, `skyConfig()`, `config(algorithm)`. Reuse them.

## Black-box vs white-box, by layer

- `core/util`, `core/solver`, `core/model`, `core/state`, `core/algorithm`: white-box. Exercise
  internal logic, branches, and edge cases directly (bitmasks, cumulative buckets, entropy,
  Memento/Observer, grid math, record guards).
- `core/service`: black-box against the in port contracts (`GenerateRegionUseCase`,
  `TerrainAlgorithmProvider`, `RuleAdminUseCase`). Use an in memory `RuleRepository` where needed.
- `adapter/out/json`: black-box round-trip against the `RuleRepository` contract with `@TempDir`.
- `adapter/in/paper`: MockBukkit for the Material registry, Mockito for `WorldInfo` and `ChunkData`,
  then assert which blocks are placed. `MaterialResolver` is package private, so its test lives in
  the same package.
- `adapter/in/command`: Mockito against the `RuleAdminUseCase` port and a mocked `CommandSender`. Do
  not depend on translatable rendering; assert delegation and the permission guard.
- `bootstrap`: MockBukkit integration. Load the plugin, assert it enables, the command is
  registered, the default world generator is the hexagonal one, and `generator.json` is written.

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
