# Contributing to SkyGrid-ng

Thanks for helping out. SkyGrid-ng follows the OneLiteFeather engineering conventions so that
maintenance stays cheap and releases stay automatic.

## Branch and PR model

- `main` is always releasable. Do not push to it directly and do not open PRs from your fork's
  `main` branch (CI will close those).
- Create a topic branch, open a PR against `main`, keep it focused.
- PRs are squash merged. The PR title must be a valid Conventional Commit, because that title
  becomes the commit on `main` that release-please reads.
- At least one CODEOWNER review is required.

## Conventional Commits

Commit messages (and squash PR titles) follow https://www.conventionalcommits.org/en/v1.0.0/.

```
<type>[optional scope]: <description>

[optional body]

[optional footer(s)]
```

Types we use and their release effect:

| Type | Meaning | Version bump |
|------|---------|--------------|
| `feat` | new user facing feature | minor |
| `fix` | bug fix | patch |
| `perf` | performance improvement | patch |
| `refactor` | internal change, no behavior change | none |
| `docs` | documentation only | none |
| `test` | tests only | none |
| `build` / `ci` | build or pipeline change | none |
| `chore` | maintenance | none |

A breaking change is marked with `!` after the type (`feat!:`) or a `BREAKING CHANGE:` footer and
triggers a major bump.

Examples:

```
feat(skygrid): add per dimension block tables
fix(spawn): correct safe-location search near the void
perf(noise): cache the value noise lattice per region
docs(admin): document view-distance tuning for 100 players
```

Scopes we use: `skygrid`, `noise`, `spawn`, `core`, `command`, `i18n`, `config`, `build`, `ci`.

## Local development

```bash
./gradlew build test       # compile, run the pure core tests
./gradlew runServer        # start a Paper 26.1 test server
```

The core (`core/`) must stay free of Bukkit, Gson, and Adventure imports so it remains unit
testable. Adapter and integration tests use MockBukkit.

## Code style

- Java 25, English identifiers and comments only.
- No em dashes in any file; use commas, colons, parentheses, or periods.
- Keep the hexagonal layering: dependencies point inward (adapters to ports to core).
- See `docs/ARCHITECTURE.md`.

## Translations

User facing strings live in `src/main/resources/messages/Bundle_*.properties`, never in code.
Translations are managed through Crowdin; do not hand edit non source bundles.
