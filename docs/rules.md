# Per-block rules

See also: [Configuration layout](configuration.md), [Presets](presets.md), [overview](README.md).

A rule controls one block. It lives in its own file under `rules/`, and there is exactly one rule per
block, so each block's behavior is tuned on its own. A rule can do three things: pin the block's data
state, decide how the block reacts to updates (physics), and, for a block that grows as a decoration
(like sugar cane), define the conditions under which it is placed.

Rules are shared by every preset: a rule for `minecraft:oak_leaves` applies wherever that block is
generated, in all algorithms. An empty `rules/` directory means "no rules", which reproduces plain
block placement.

## The file

The block id inside the file (`block:`) is authoritative; the file name is only for humans.

```yaml
# rules/oak_leaves.yml
block: minecraft:oak_leaves   # namespaced id, required
state:                        # block-data properties, optional (empty = plain default state)
  persistent: true
physics: none                 # none | block_update | fluid_update (default: none)
placement:                    # optional; present only for a grown decoration
  requiresGround: minecraft:sand
  requiresLiquidNeighbor: false
  water: source               # none | source | waterlogged (default: none)
  minHeight: 1
  maxHeight: 3
  probability: 0.04
```

A rule with a `placement` section is a **decoration** (it grows a column somewhere on the grid). A
rule without one is a plain **palette-block modifier**: it only adjusts the state and physics of a
block that the palette already places.

## `state`: block-data properties

`state` is a map of block-data property names to values, written as strings (`persistent: true`,
`level: 0`, `waterlogged: true`). SkyGrid-ng turns the block id plus this map into a native block
state, effectively `minecraft:oak_leaves[persistent=true]`.

- An empty or absent `state` means "place the block's default state".
- If a property is not valid for the block, the rule quietly falls back to the plain default state
  rather than failing generation.

## `physics`: how the block reacts to updates

`physics` decides whether the placed block is scheduled for a block or fluid update after generation.

| Value          | Effect |
|----------------|--------|
| `none`         | Place the block with no update. Nothing flows, decays, falls, or recomputes. |
| `block_update` | Schedule a block update: support, gravity, and redstone checks run. |
| `fluid_update` | Schedule a fluid update: the fluid starts to flow from this block. |

`none` is the default and the safe, stable choice, and it is why the shipped rules use it:

- **Leaves do not decay.** Vanilla leaves check for a nearby log and decay if none is found. On a
  sparse SkyGrid there usually is no adjacent log, so a block update would make leaves disappear.
- **Fluids do not flow.** A water or lava source left free on the grid would flow down into the void
  and spread, flooding the world and firing a storm of updates. `none` keeps it put.
- **Sand and gravel do not fall.** Gravity-affected blocks stay in place on the grid.

Use `block_update` or `fluid_update` only when you deliberately want a single block to opt back into
vanilla behavior. For a generator that must stay deterministic and lag-free, `none` is almost always
what you want.

**Scope:** `block_update` and `fluid_update` take effect for a **decoration** rule (one with a
`placement`), where a single targeted update is scheduled at the plant. A plain **palette-block**
modifier (a rule without `placement`) is written into the raw chunk during generation, which has no
update to schedule, so its physics is always effectively `none`. This is deliberate: scheduling
updates across every grid block would cause the update storms `none` exists to prevent. Set a
non-`none` mode on a decoration rule, not a palette one.

## `placement`: growing a decoration

Present only when the rule grows a decoration column. Fields:

| Field                    | Type      | Meaning |
|--------------------------|-----------|---------|
| `requiresGround`         | block id  | The support block the plant must stand on. The adapter also lays this block as the support under the plant. Omit for none. |
| `requiresLiquidNeighbor` | boolean   | If true, a horizontal neighbor of the support must be liquid. |
| `water`                  | enum      | How the plant is watered: `none`, `source`, or `waterlogged`. Default `none`. |
| `minHeight`              | integer   | Minimum column height in blocks (at least 1). |
| `maxHeight`              | integer   | Maximum column height (at least `minHeight`). |
| `probability`            | number    | Placement chance per eligible column, in `[0, 1]`. |

The generator scans grid cells, rolls `probability` per cell (seeded, see
[Determinism](#determinism)), and on a hit lays the support, provides water, grows the column, and
applies the rule's physics.

## Water modes explained

`water` says how a plant gets the water it needs. The distinction matters because of one hard
Minecraft fact: **sand cannot be waterlogged.**

- **`none`**: provide no water. The plant is placed only where its other conditions already hold.
- **`source`**: place a **contained still-water source** in a neighbor cell. The generator boxes a
  single water source in on the floor and all four sides (the plant's own block is one wall, three
  more walls and a floor are placed around it), leaving only the top open. A source with no air to
  flow into never ticks, so it stays put, fires no update, and never floods the void, while still
  being real water the plant can drink from. This is the correct mode for **sugar cane on sand**,
  because sand cannot hold water itself.
- **`waterlogged`**: waterlog the **support block itself**. This only works when the support is a
  block that can actually hold water (a waterloggable block); on a support that cannot be waterlogged
  (such as sand) it does nothing. Use it when the plant's support is genuinely waterloggable.

Rule of thumb: use `waterlogged` when the support can hold water, and `source` when it cannot (the
sand and sugar cane case).

## Worked examples from the shipped defaults

### `oak_leaves`: persistent, no decay

Leaves are pinned to `persistent=true` so they are never checked for decay, and `physics: none` so
placement does not trigger that check. They survive on the grid even with no adjacent log. (The
`modern` preset's `cherry_leaves` rule is identical in shape.)

```yaml
# rules/oak_leaves.yml
block: minecraft:oak_leaves
state:
  persistent: true
physics: none
```

### `water`: a stable source that never floods

Generated water is pinned to `level=0` (a full source block) with `physics: none`, so it never flows
out and floods the void around the grid.

```yaml
# rules/water.yml
block: minecraft:water
state:
  level: 0
physics: none
```

### `sugar_cane`: grown on sand, watered by a contained source

Sugar cane is a decoration column. It stands on sand (`requiresGround: minecraft:sand`) and is
watered with `water: source`, because sand cannot be waterlogged, so a boxed-in still-water source in
a neighbor is the correct way to water it without flooding. It is placed with `physics: none` so the
support and water checks never break it off. `probability: 0.04` keeps it sparse, and it grows 1 to 3
blocks tall.

```yaml
# rules/sugar_cane.yml
block: minecraft:sugar_cane
physics: none
placement:
  requiresGround: minecraft:sand
  water: source
  minHeight: 1
  maxHeight: 3
  probability: 0.04
```

Note this rule has no `state` section: it does not need to pin any block-data property, so the sugar
cane uses its default state.

## Determinism

Everything a rule does is seeded from the world seed. Each cell's decoration roll is derived from the
world seed and the cell coordinates (with the rule's block id mixed in), so the same seed and
coordinates always produce the same result, independent of the order chunks are populated. A config
change therefore only affects newly generated chunks; it never changes how an existing seed maps to a
world.
