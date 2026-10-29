# Extending SkyGrid-ng

Step by step recipes for the common changes. Each one keeps the hexagonal rule intact: the change
stays in one layer and the Paper edge does not move.

## Add a generation algorithm

The Paper chunk generator never changes; it always asks `TerrainAlgorithms.current()`.

1. Create a class in `core/algorithm` implementing `TerrainAlgorithm`. Give it a stable
   `public static final String ID`. Implement `generateColumn(...)` by emitting `(y, materialName)`
   pairs to the `ColumnSink`. Use `Seeds` for determinism and `WeightedTable` or `ValueNoise` for
   block choice.
2. Register an `AlgorithmFactory` for the id in the composition root's Guice `MapBinder` (read by
   `DefaultTerrainAlgorithms`'s `Registry`, which is the single source of truth for `isKnown` and
   `ids()`). The core does not gain a new `switch` branch.
3. Command tab completion for `/skygrid algorithm` is registry driven
   (`RuleAdminUseCase.algorithmIds()`), so a new id shows up automatically; no command file to touch.
4. If the algorithm needs new config, extend `SkyGridConfig` (a record, validate in the compact
   constructor) and the config DTO in the config adapter.
5. Tests: a white-box test in `core/algorithm` for the new algorithm and, if the registry wiring
   changed, an assertion in `DefaultTerrainAlgorithmsTest`. See the `testing` skill.

Do not add Bukkit types to the algorithm. Material names are plain strings; the adapter resolves
them.

## Add a command subcommand

Commands run on Incendo Cloud (`command`), annotation driven
(`org.incendo.cloud.annotations`), not a hand-rolled `CommandExecutor`/`TabCompleter` switch.

1. Add an `@Command("skygrid <literal> ...")`/`@Permission("skygridng.admin")` annotated method to
   an existing `command` class (group by concern, e.g. `AlgorithmCommands`,
   `ChallengeCommands`), or create a new class implementing `CloudCommandHandler` if it's a new
   concern. The method's first parameter is always `CommandSource source` (the platform-neutral
   sender), never a Bukkit `CommandSender`; required syntax fragments are `<name>`, optional ones
   `[name]`, matching `@Argument` parameters (`@Nullable` for optional).
2. If it's a new handler class, bind it in `command.CommandModule`'s
   `Multibinder<CloudCommandHandler>` (one line). `CloudCommandRegistrar` parses the whole set
   automatically; existing handler classes need no change elsewhere.
3. Delegate to a `RuleAdminUseCase`/`Presets`/`ChallengeAdminUseCase` method. If the operation is
   new, add it to the relevant port and implement it in the matching `core.service` class (mutate
   via `RuleState.apply` so it is undoable, or `replace` for a reload).
4. Add a translatable line to `Messages` and `resources/messages/Bundle_en.properties` (source of
   truth) under the `skygridng.command.` prefix, written as MiniMessage (color/click/hover tags,
   `<arg:0>`/`<arg:1>` for `Component.translatable` arguments, not `java.text.MessageFormat`'s
   `{0}`/`{1}`). Never hand-edit `Bundle_de.properties`; Crowdin syncs the German translation from
   the English source.
5. If the argument needs dynamic suggestions (not a fixed literal Cloud already suggests natively),
   add `@Argument(value = "...", suggestions = "some-name")` and register
   `"some-name"` on `manager.parserRegistry()` in `CloudCommandRegistrar`.
6. Tests: a unit test against the new/changed handler class using a fake `CommandSource` (see
   `RecordingCommandSource` in `plugin/src/test`), no MockBukkit needed since handler
   classes never import Bukkit. If the port grew, also extend the matching `core.service` test.

## Add an out adapter (for example a database repository)

1. Implement one of the existing `ports.out` interfaces (`PresetRepository`, `RuleRepository`,
   `ChallengeRepository`) in a new package under `plugin` (or a new adapter module). The
   core does not change.
2. Bind it in `RepositoryModule` instead of the matching `Jackson*Repository`. Nothing else moves.
3. Tests: a black-box round-trip test against the port's contract, like
   `JacksonPresetRepositoryTest`/`JacksonRuleRepositoryTest`/`JacksonChallengeRepositoryTest`.

## Add a config field

1. Add the component to the relevant record (`SkyGridConfig`, `Preset`, `Rule`, `PlacementRule`, ...,
   see `config-schema-and-rules` for the full model) and validate it in the compact constructor.
2. Mirror it in the matching Jackson DTO (`ConfigDtos`, `RuleDtos`, or `ChallengeDtos` in
   `plugin`) and update the bundled defaults under
   `plugin/src/main/resources/` (`presets/`, `rules/`, or `challenges/`).
3. Update the relevant operator doc (`docs/configuration.md`, `docs/presets.md`, or `docs/rules.md`)
   so server operators know about it.
4. Tests: a guard test in `RecordValidationTest` and a round-trip assertion in the matching
   `Jackson*RepositoryTest`.

## Anti patterns to reject

- Importing `org.bukkit.*`, Gson, Guice, or `net.kyori.adventure.platform` into any `core` class
  (plain Adventure text, `net.kyori.adventure`, is allowed).
- Calling a service implementation from an adapter instead of the port interface.
- Reintroducing the removed WFC method names (`WfcSolver`, `WfcSurfaceAlgorithm`, algorithm id
  `wfc`) or a `core.solver` package. The Wave Function Collapse algorithm was removed; only `skygrid`
  and `noise_skygrid` remain.
- Mutating a record in place. Records are immutable; use the `with...` copy methods.
