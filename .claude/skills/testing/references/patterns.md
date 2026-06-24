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
assertThat(plugin.getCommand("skygrid")).isNotNull();
assertThat(new java.io.File(plugin.getDataFolder(), "generator.json")).exists();
```

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

Command test against the port, no MockBukkit and no message rendering:

```java
RuleAdminUseCase admin = mock(RuleAdminUseCase.class);
CommandSender sender = mock(CommandSender.class);
SkyGridCommand cmd = new SkyGridCommand(admin);

when(sender.hasPermission("skygridng.admin")).thenReturn(false);
cmd.onCommand(sender, mock(Command.class), "skygrid", new String[]{"list"});
verify(sender).sendMessage(any(net.kyori.adventure.text.Component.class));
verifyNoInteractions(admin);
```

In memory repository for service tests:

```java
RuleRepository repo = new RuleRepository() {
    private GeneratorConfig stored = Fixtures.config("skygrid");
    public GeneratorConfig load() { return stored; }
    public void save(GeneratorConfig c) { stored = c; }
};
```

## Verified value table

These were confirmed against the real core. If you change the relevant logic, re-verify and update.

- `Seeds.unitDouble` in `[0,1)`; `mix/mix3/blockSeed` deterministic; `mix3` reacts to every
  coordinate; the named seed spaces (region, cell, boundary) differ.
- `WeightedTable` of `[A:1,B:1,C:2]`: size 3, total 4; `sample(0)=A`, `sample(0.4)=B`,
  `sample(0.99)=C`, `sample(1.0)=C` (clamped); distribution about 25/25/50.
- `ValueNoise`: range within `[0,1)`; stable per integer lattice point; a tiny step gives a tiny
  delta (< 0.05).
- Fixtures adjacency (water0, sand1, grass2, cactus3; pairs water-sand, sand-grass, sand-cactus,
  grass-cactus, allowSelf true): support(water)=`0b0011`, support(sand)=`0b1111`,
  support(cactus)=`0b1110`; without self, support(water)=`0b0010`. More than 64 tiles throws.
- `RuleSet`: fullMask=`0b1111`, neutralMask=`0b0111` (cactus not neutral); indices water0..cactus3;
  `withAllowed` 4 -> 5, `withoutAllowed` 4 -> 3; empty tiles, unknown fallback, and
  `tileIndex(unknown)` throw `IllegalArgumentException`; `tileById(unknown)` is empty.
- Shannon entropy: single bit = 0.0; full `0b1111` (1.2407) > two `0b0011` (0.6730).
  WeightedRandomCollapse: always in mask, single bit returns that index, mask `0b0101` returns grass
  about 0.625 of the time (weight 10 of 16).
- `WfcSolver` 6x6 with full pairs and allowSelf: length 36, zero adjacency violations, deterministic,
  all indices valid; a single bit preset pins that cell; no pairs and no self fills every cell with
  the fallback index.
- `RuleState`: apply fires once and pushes undo; undo restores, fires, returns true; undo on empty
  history returns false; history is bounded by `maxUndo`; replace clears the undo stack.
- `RuleAdminService` (in memory repo): `algorithm()`=skygrid; `setAlgorithm(wfc)`=true,
  `setAlgorithm(bogus)`=false; `tileIds()`=[water,sand,grass,cactus]; `setWeight(known)`=true,
  `setWeight(unknown)`=false; `disallow`/`allow`/`setSkyGridWeight`=true; `save` persists; `undo`=true.
- SkyGrid algorithms: off grid column empty; on grid (8,12) with y 0..64 step 4 has 17 blocks, all
  `y % 4 == 0`, deterministic; the noise variant clusters neighbours more than the independent one.
- `WfcSurfaceAlgorithm` (seed 987654321, world 10,12, minY 0, maxY 200, surfaceY 64): column 0..64
  contiguous, size 65; the block at y 64 equals `region.tileAt(seed, 2, 3).surface()`.
