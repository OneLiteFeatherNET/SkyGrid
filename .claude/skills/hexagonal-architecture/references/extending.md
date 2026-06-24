# Extending SkyGrid-ng

Step by step recipes for the common changes. Each one keeps the hexagonal rule intact: the change
stays in one layer and the Paper edge does not move.

## Add a generation algorithm

The Paper chunk generator never changes; it always asks `TerrainAlgorithmProvider.current()`.

1. Create a class in `core/algorithm` implementing `TerrainAlgorithm`. Give it a stable
   `public static final String ID`. Implement `generateColumn(...)` by emitting `(y, materialName)`
   pairs to the `ColumnSink`. Use `Seeds` for determinism and `WeightedTable` or `ValueNoise` for
   block choice. Set `usesWfcDecorations()` to true only if it needs the decoration populator.
2. Register the id in `AlgorithmService`: add the construction branch in the factory and add the id
   to `isKnown`.
3. Add the id to the command tab completion list `ALGORITHMS` in `SkyGridCommand`.
4. If the algorithm needs new config, extend `SkyGridConfig` or `RuleSet` (records, validate in the
   compact constructor) and the JSON DTO in `adapter/out/json`.
5. Tests: a white-box test in `core/algorithm` for the new algorithm and, if the factory changed, an
   assertion in `AlgorithmServiceTest`. See the `testing` skill.

Do not add Bukkit types to the algorithm. Material names are plain strings; the adapter resolves
them.

## Add a command subcommand

1. Add the keyword to the `SUBCOMMANDS` list and a `case` in `SkyGridCommand.onCommand`.
2. Delegate to a `RuleAdminUseCase` method. If the operation is new, add the method to the
   `RuleAdminUseCase` port and implement it in `RuleAdminService` (mutate via `RuleState.apply` so it
   is undoable, or `replace` for a reload).
3. Add a translatable line to `Messages` and both bundle files
   (`resources/messages/Bundle_en.properties`, `Bundle_de.properties`) under the `skygridng.command.`
   prefix. Keep keys in sync across locales; Crowdin syncs translations.
4. Update tab completion in `onTabComplete` if the subcommand takes arguments.
5. Tests: extend `SkyGridCommandTest` (Mockito against the port) and, if the port grew,
   `RuleAdminServiceTest`.

## Add an out adapter (for example a database repository)

1. Implement the existing `core/port/out/RuleRepository` interface in a new package under
   `adapter/out`. The core does not change.
2. Bind it in `RepositoryModule` instead of `JsonRuleRepository`. Nothing else moves.
3. Tests: a black-box round-trip test against the `RuleRepository` contract, like
   `JsonRuleRepositoryTest`.

## Add a config field

1. Add the component to the relevant record (`SkyGridConfig`, `RuleSet`, `GenerationSettings`, ...)
   and validate it in the compact constructor.
2. Mirror it in the Gson DTO in `adapter/out/json/GeneratorConfigDto` and update the default
   `resources/generator.json`.
3. Update `docs/ADMIN.md` so server operators know about it.
4. Tests: a guard test in `RecordValidationTest` and a round-trip assertion in
   `JsonRuleRepositoryTest`.

## Anti patterns to reject

- Importing `org.bukkit.*`, Gson, Guice, or Adventure into any `core` class.
- Calling a service implementation from an adapter instead of the port interface.
- Renaming the WFC method names (`WfcSolver`, `WfcSurfaceAlgorithm`, id `wfc`) as if they were
  project branding. They describe the algorithm and stay.
- Mutating a record in place. Records are immutable; use the `with...` copy methods.
