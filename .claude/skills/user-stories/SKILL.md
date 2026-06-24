---
name: user-stories
description: >
  Use whenever planning, implementing, reviewing, or prioritizing a feature for SkyGrid-ng, to keep
  it aligned with the functional requirements. Captures the user stories by role (server owner,
  server admin, map maker, maintainer, developer) and epic (SkyGrid revival, switchable algorithms,
  retained WFC, internationalization, architecture), each with its acceptance criteria. Read this
  before adding or changing a feature so it satisfies a real user need, and treat the acceptance
  criteria as the definition of done before calling a feature complete.
---

# SkyGrid-ng user stories

The functional requirements, written as "As a ROLE, I want GOAL, so that BENEFIT". The canonical,
full text lives in `docs/USERSTORIES.md`; keep that file and this skill in sync when requirements
change. Use the acceptance criteria as the definition of done: a feature is not finished until its
criteria hold and there are tests (see the `testing` skill).

## Roles

Server owner (runs the server), server admin (configures and tunes), map maker (builds with WFC
terrain), maintainer (keeps the code healthy), developer (extends the plugin).

## Epic A: SkyGrid revival

- A1 Classic SkyGrid world: an infinite grid of single blocks every `spacing` blocks (default 4) in
  the void. Empty except grid blocks; configurable spacing; seed based (same seed and coordinates
  give the same block); configurable weighted block table.
- A2 Coherent (clustered) SkyGrid: same grid, but block choice is biased by seed based value noise so
  neighbors cluster; cluster size configurable via `noiseScale`.
- A3 Configurable block table: each entry has a material name and weight; unknown materials degrade
  gracefully to a safe fallback, never crash.

## Epic B: Switchable generation algorithms

- B1 Choose by config: `algorithm` accepts `wfc`, `skygrid`, `noise_skygrid`; an unknown value falls
  back to a documented default with a warning.
- B2 Switch in game: `/skygrid algorithm <id>` changes the active algorithm; new chunks use it; the
  change is reversible with undo.
- B3 Fast seed based generation: SkyGrid generation is O(1) per grid cell (no constraint
  propagation), deterministic and parallel safe.

## Epic C: WFC terrain (retained)

- C1 Adjacency based surface terrain: configurable tiles, weights, and allowed neighbor pairs; region
  seams are adjacency correct and seamless.
- C2 In game rule editing: `/skygrid weight`, `allow`, `disallow`, `undo`, `reload`, `save`; changes
  apply immediately to newly generated chunks.

## Epic D: Internationalization

- D1 Localized messages: all player facing command messages are translatable and render in the
  viewer's client locale (server side via Adventure); English and German shipped, more by dropping in
  a bundle.
- D2 English code base: all identifiers, comments, and docs are English; user facing strings live in
  translation bundles, not in code.

## Epic E: Architecture and extensibility (retained)

- E1 Framework independent core: `core/` has no `org.bukkit` or `com.google.gson` imports; algorithms
  are unit testable without a server.
- E2 Pluggable algorithm port: a new algorithm is one class implementing `TerrainAlgorithm` plus one
  factory branch; the chunk generator does not change.

## Using these stories

When you pick up work, find the story it serves and make the acceptance criteria the checklist. If a
request has no matching story, it is either a new requirement (add a story to `docs/USERSTORIES.md`
first) or out of scope (say so). Cross reference: the `skygrid-domain` skill explains the generation
behavior these stories describe, and the `hexagonal-architecture` skill explains how to implement E1
and E2 without breaking the core.
