# Testing patterns

Concrete snippets, the empirical verification workflow, and the table of already verified values.

## Contents

- Empirical verification workflow
- MockBukkit patterns
- Mockito patterns
- Verified value table

## Empirical verification workflow

The core compiles standalone, so you can confirm an expected value before asserting it.

1. Compile the core: `.claude/skills/hexagonal-architecture/scripts/compile-core.sh` writes classes
   to a temp dir, or do it manually:
   ```bash
   find src/main/java/net/onelitefeather/skygridng/core -name '*.java' > /tmp/core.txt
   javac -d /tmp/out @/tmp/core.txt
   ```
2. Write a tiny driver that calls the real core and prints the value, then run it:
   ```bash
   javac -cp /tmp/out -d /tmp/out /tmp/Verify.java && java -cp /tmp/out Verify
   ```
3. Assert exactly the printed value in the JUnit test.

This is how the values below were established. Re-verify after changing any core logic.

## MockBukkit patterns

```java
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

private ServerMock server;

@BeforeEach void setUp() { server = MockBukkit.mock(); }
@AfterEach  void tearDown() { MockBukkit.unmock(); }
```

Load the plugin for an integration test (reads the generated plugin.yml from the test classpath):

```java
SkyGridNgPlugin plugin = MockBukkit.load(SkyGridNgPlugin.class);
assertThat(plugin.isEnabled()).isTrue();
assertThat(new java.io.File(plugin.getDataFolder(), "config.yml")).exists();
assertThat(new java.io.File(plugin.getDataFolder(), "presets/classic.yml")).exists();
```

Commands are no longer registered via `plugin.getCommand(...)` (Cloud registers them at runtime, see
`hexagonal-architecture`); do not assert on it. The Cloud command manager's construction throws under
MockBukkit and `SkyGridNgPlugin` guards that, so do not assert anything about it being built here
either.

Generator test: MockBukkit gives the Material registry, Mockito gives the chunk inputs, then capture
which blocks land.

```java
WorldInfo world = mock(WorldInfo.class);
when(world.getSeed()).thenReturn(Fixtures.SEED);
ChunkData data = mock(ChunkData.class);
when(data.getMinHeight()).thenReturn(0);
when(data.getMaxHeight()).thenReturn(64);

generator.generateSurface(world, new Random(0), 0, 0, data);

ArgumentCaptor<Integer> y = ArgumentCaptor.forClass(Integer.class);
verify(data, times(256)).setBlock(anyInt(), y.capture(), anyInt(), any(Material.class));
assertThat(y.getAllValues()).allMatch(v -> v % 4 == 0);
```

`ChunkData` is the nested `org.bukkit.generator.ChunkGenerator.ChunkData`. With a SkyGrid spacing 4
config over a 16x16 chunk and y range 0..63, exactly 256 blocks are placed (4 x 4 grid columns,
16 grid heights each).

## Mockito patterns

Command handler test against the port, no MockBukkit and no message rendering: call the
`@Command`-annotated method directly (Cloud's argument parsing and permission gate are not part of
what this test exercises) with a `CommandSource` test double instead of a mocked Bukkit
`CommandSender`. `adapters/command/src/test/java/.../adapters/command/handler/RecordingCommandSource.java`
is that double: it just records every `Component` sent, in order.

```java
RuleAdminUseCase admin = mock(RuleAdminUseCase.class);
MaintenanceCommands subject = new MaintenanceCommands(admin);
RecordingCommandSource source = new RecordingCommandSource();

subject.reload(source);

verify(admin, never()).reload();
assertThat(source.messages()).containsExactly(Messages.reloadConfirm());
```

See `MaintenanceCommandsTest` (and its four sibling `*CommandsTest` classes in the same package) for
the full pattern across all five migrated handler classes.

In memory repository for service tests, prefer the shared fixture over a one-off anonymous class:
`Fixtures.InMemoryPresetRepository` (`core/src/testFixtures/.../testutil/Fixtures.java`) wraps a
`PresetCatalog` the same way `RuleAdminServiceTest` constructs it:

```java
PresetCatalog catalog = Fixtures.presetCatalog("skygrid");
Fixtures.InMemoryPresetRepository repository = new Fixtures.InMemoryPresetRepository(catalog);
```

A bare in memory `RuleRepository`, if a test genuinely needs one directly, implements
`RuleCatalog load()`/`void save(RuleCatalog)` (not `GeneratorConfig`, see `hexagonal-architecture`'s
`references/api-surface.md`).

## Verified value table

These were confirmed against the real core. If you change the relevant logic, re-verify and update.

The `wfc` algorithm and its model (`RuleSet`, `AdjacencyRules`, `TileType`, `GenerationSettings`) and
solver (`WfcSolver`, `ShannonEntropyHeuristic`, `WeightedRandomCollapse`) were removed; the values that
used to be verified against them (tile adjacency masks, Shannon entropy, `WfcSolver` region solving,
`WfcSurfaceAlgorithm` columns) no longer apply and are not replaced by anything, since the two
remaining algorithms need no adjacency solving step.

- `Seeds.unitDouble` in `[0,1)`; `mix/mix3/blockSeed` deterministic; `mix3` reacts to every
  coordinate; the named seed spaces (region, cell, boundary) differ.
- `WeightedTable` of `[A:1,B:1,C:2]`: size 3, total 4; `sample(0)=A`, `sample(0.4)=B`,
  `sample(0.99)=C`, `sample(1.0)=C` (clamped); distribution about 25/25/50.
- `ValueNoise`: range within `[0,1)`; stable per integer lattice point; a tiny step gives a tiny
  delta (< 0.05).
- `RuleState`: apply fires once and pushes undo; undo restores, fires, returns true; undo on empty
  history returns false; history is bounded by `maxUndo`; replace clears the undo stack.
- `RuleAdminService` (in memory preset catalog via `Fixtures.presetCatalog`/`InMemoryPresetRepository`):
  `algorithm()`=skygrid; `setAlgorithm(noise_skygrid)`=true, `setAlgorithm(bogus)`=false;
  `setSkyGridWeight` true for an existing or a new material, false for a non positive weight or an
  invalid block id; `save` flattens the edit into the active preset; `undo`=true and restores the
  prior algorithm.
- SkyGrid algorithms: off grid column empty; on grid (8,12) with y 0..64 step 4 has 17 blocks, all
  `y % 4 == 0`, deterministic; the noise variant clusters neighbours more than the independent one.
