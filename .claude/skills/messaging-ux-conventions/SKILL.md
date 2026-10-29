---
name: messaging-ux-conventions
description: >-
  Explains SkyGrid-ng's player- and admin-facing feedback surfaces: the four channels (chat, action
  bar, boss bar, scoreboard) and when each applies, the current i18n convention for chat messages
  (Bundle_en/Bundle_de.properties, skygridng.command.* keys, Adventure Component.translatable
  rendered via MiniMessageTranslationStore with <arg:N> placeholders and MiniMessage tags for
  color/click/hover), how to compose multi-line feedback as multiple Audience.sendMessage calls per
  the docs.papermc.io/adventure reference, the current state of server log messages (plain, untemplated,
  not localized), and the admin versus player audience split. Use when wording a new command message,
  choosing which channel a new piece of feedback should use, deciding on a new i18n key name, or a
  message needs more than one line. Cross-links hexagonal-architecture (where the message code lives)
  and project-context (why Adventure was chosen); does not restate either.
---

# Messaging and UX conventions

This skill owns **what surface a piece of feedback should use and how it should read**: channel
choice, wording conventions, and the current state of i18n and logging. It does not own:

- perceptual accessibility of these surfaces (color-independence, not relying on color alone,
  comfort framing): see the `accessibility-designer` agent.
- where the message code architecturally lives (which module, which class): see
  `hexagonal-architecture`.
- why Adventure was chosen as the text API in the first place: see `project-context`.

Cross-link those; do not restate them here.

## The four feedback channels

SkyGrid-ng has one of these four channels built today (chat); the other three are forward looking
guidance for when they get built, not a description of existing code. A repository-wide grep for
"bossbar" and "scoreboard" (case-insensitive) returns zero hits: neither exists yet.

- **Chat.** Persistent scrollback (`Audience.sendMessage`). Use for command responses, errors, and
  discrete important one-off events the player or admin should be able to scroll back to. This is
  the only channel SkyGrid-ng uses today, via `/skygrid`'s subcommands.
- **Action bar.** Transient, non-blocking (`Audience.sendActionBar`). Use for a brief status hint
  that doesn't need to persist and shouldn't interrupt or clutter chat, for example a short
  confirmation that fades on its own.
- **Boss bar.** Adventure `BossBar.bossBar(name, progress, Color, Overlay)`, mutable: the same bar
  instance can have its name, progress (0..1), color, or overlay updated in place without resending.
  Use for an ambient, persistent-while-active, top-of-screen status or progress indicator, for
  example a future region-generation progress indicator that updates as chunks resolve. Not built
  yet; no `BossBar` usage exists in this repository today.
- **Scoreboard.** Persistent glanceable sidebar for ongoing stats the player should be able to check
  at a glance without opening anything. Use for a running count or status that stays relevant over a
  session. Not built yet; no scoreboard usage exists in this repository today.

## Current i18n reality and naming convention

Chat messages are the one real, shipping surface:

- Key namespace: `skygridng.command.*` (one key per command message, for example
  `skygridng.command.weight.set`, `skygridng.command.algorithm.unknown`) and one exception,
  `skygridng.api.summary`, used by `SkyGridApi.describe()`.
- Bundles: flat `key=value` `.properties` files,
  `plugin/src/main/resources/messages/Bundle_en.properties` (source of truth) and
  `Bundle_de.properties` (translated, flows through Crowdin, never hand-edited directly).
- Registration: `plugin/src/main/java/net/onelitefeather/skygridng/platforms/common/I18n.java`
  registers both locales with Adventure's `GlobalTranslator` via
  `MiniMessageTranslationStore` (`net.kyori.adventure.text.minimessage.translation`), so a
  `Component.translatable(key, args...)` renders in each viewer's own client locale server side.
  Bundle values are parsed as **MiniMessage**, not `java.text.MessageFormat`: use MiniMessage tags
  for color/decoration/click/hover (`<red>`, `<click:suggest_command:'...'>`, etc.), and reference
  `Component.translatable` arguments positionally as `<arg:0>`, `<arg:1>`, ... (not `{0}`, `{1}`).
  MiniMessage's default instance is lenient, an unrecognized tag (for example a literal `<name>`
  placeholder in a usage string like `/skygrid preset <name>`) renders as plain text instead of
  throwing, so existing `<placeholder>`/`[optional]` usage syntax stays safe without escaping.
- Construction: `plugin/src/main/java/net/onelitefeather/skygridng/adapters/command/Messages.java`
  builds every `Component.translatable(...)` call; the callers today are the five
  `@Command`-annotated handler classes in `plugin/src/main/java/.../adapters/command/handler/`
  (`PresetCommands`, `AlgorithmCommands`, `MaintenanceCommands`, `ChallengeCommands`, `HelpCommands`),
  run through Incendo Cloud rather than a hand-rolled `CommandExecutor`.

Going forward, name a new key `skygridng.<area>.<subarea>[.<detail>]`, following the existing
`skygridng.command.<subcommand>[.<detail>]` shape (for example `skygridng.command.weight.set`,
`skygridng.command.weight.fail`). When a new surface (action bar, boss bar, scoreboard) gets built,
give it its own `<area>` segment (for example `skygridng.bossbar.*`) rather than overloading
`command`.

## Composing multi-line or multi-part feedback

Authoritative reference: [docs.papermc.io/adventure](https://docs.papermc.io/adventure/), especially
the [Text](https://docs.papermc.io/adventure/text/), [Audiences](https://docs.papermc.io/adventure/audiences/),
and [Localization](https://docs.papermc.io/adventure/localization/) pages. Consult these (or the
[Javadoc](https://jd.advntr.dev)) before inventing a workaround for something Adventure already has a
documented way to do.

When a piece of feedback needs more than one line, call `Audience.sendMessage(...)` once per line
instead of concatenating everything (including a literal `\n`) into a single translatable/MessageFormat
string. Sending discrete messages:

- avoids relying on undocumented newline-in-a-single-component rendering behavior,
- lets each line have its own bundle key (so one line can change independently, or be reused
  elsewhere), and
- keeps each MiniMessage pattern simple, which matters once a line has its own `<arg:0>`/`<arg:1>` args.

Concrete precedent: `Messages.help()` in
`plugin/src/main/java/net/onelitefeather/skygridng/adapters/command/Messages.java` returns
a `List<Component>` (`skygridng.command.help.header`, `.subcommands`, `.algorithm_skyweight`,
`.reload_save_undo_challenge`, `.footer`), and `HelpCommands` (in `adapters/command/handler`) sends
each with its own `source.sendMessage(...)` call. Use this shape, not a single newline-joined
`key("help")`, whenever a message needs more than one visual line.

## Current log message reality

`SkyGridNgPlugin.java` and `WorldGenerators.java` (both in
`plugin/src/main/java/net/onelitefeather/skygridng/platforms/common/`) call
`getLogger().info(...)` and `getLogger().warning(...)` with plain, hand-concatenated strings, for
example `"Unknown algorithm '" + algorithm + "', falling back to WFC."`. These are not localized and
follow no shared template today. State this as the current state, not a defect this skill mandates
fixing; a consistent log message format is a legitimate future improvement to propose, not something
to change opportunistically inside an unrelated task.

## Audience split

- **Admin-facing.** Command responses and errors (read by whoever runs `/skygrid`) and server log
  messages (read by operators watching the console). This audience can handle more technical detail:
  tile ids, config key names, algorithm identifiers are all fine here.
- **Player-facing.** Boss bar and scoreboard content, and any in-game chat shown during normal
  gameplay rather than administration. This audience needs text that stays in theme, stays terse, and
  never surfaces a stack trace, an internal id, or a raw exception message.

## Where to look

- `hexagonal-architecture`: where `I18n`, `Messages`, and the command adapter architecturally live,
  and the dependency rule that keeps them out of `api`/`ports`/`core`.
- `project-context`: why Adventure-based i18n was chosen over an alternative.
- `accessibility-designer` agent: perceptual accessibility of these same surfaces (color-independence,
  comfort presets); consult alongside `interface-ux-designer` whenever a change touches one of these
  surfaces.

Code to read for real examples:

- `plugin/src/main/java/net/onelitefeather/skygridng/platforms/common/I18n.java`
- `plugin/src/main/resources/messages/Bundle_en.properties`, `Bundle_de.properties`
- `plugin/src/main/java/net/onelitefeather/skygridng/adapters/command/Messages.java`
- `plugin/src/main/java/net/onelitefeather/skygridng/adapters/command/handler/` (the
  `@Command`-annotated handler classes that call `Messages`)
- `plugin/src/main/java/net/onelitefeather/skygridng/platforms/common/SkyGridNgPlugin.java`,
  `WorldGenerators.java`
