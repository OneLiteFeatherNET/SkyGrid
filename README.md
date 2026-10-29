# SkyGrid-ng

A chunk generator plugin for Paper 26.1 that revives the classic **SkyGrid**. Pure Java 25, hexagonal
architecture, i18n through Adventure and MiniMessage, YAML configuration, switchable at
runtime via `/skygrid` (built on the Incendo Cloud command framework).

## Algorithms

Selected by a preset's `algorithm` field, switchable in game with `/skygrid algorithm <id>`:

- **`skygrid`** (default): classic SkyGrid. One block on every grid point (grid length `spacing`,
  default 4), air between, floating in the void. Each cell is sampled independently from a
  weighted table by a coordinate hash. O(1) per cell, parallel, fastest. Faithful to SethBling's
  2012 SkyGrid.
- **`noise_skygrid`**: same grid, but block choice follows a seed based value noise field so
  similar blocks cluster into veins and themed regions. Also O(1) per cell.

All algorithms are seed based and deterministic.

## Quick start

1. Put `skygrid-ng.jar` in `plugins/` and start the server once. This seeds
   `plugins/SkyGrid-ng/` with the bundled default `config.yml`, `presets/`, and `rules/` (see
   [docs/README.md](docs/README.md) for the full layout).
2. Add a world using the generator in `bukkit.yml`:

   ```yaml
   worlds:
     skygrid:
       generator: SkyGrid-ng
   ```

3. Load the world and play. See [docs/ADMIN.md](docs/ADMIN.md) for commands and tuning advice for
   large servers (around 100 players), and [docs/README.md](docs/README.md) for the configuration
   reference (presets, per-block rules, formats).

## Build

```bash
./gradlew build                    # compile and test every module
./gradlew :plugin:runServer # start a Paper 26.1 test server
```

The shaded plugin jar is at `plugin/build/libs/skygrid-ng-<version>.jar`. SkyGrid-ng is a
three-module project (`domain` for the framework-free api, ports and core; `plugin` for everything that talks to Paper; `verification` for the ArchUnit rules); see
[docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).

## Documentation

- [docs/ADMIN.md](docs/ADMIN.md): commands and performance tuning.
- [docs/README.md](docs/README.md): the configuration reference (layout, formats, presets, per-block
  rules).
- [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md): hexagonal design and extension points.
- [docs/USERSTORIES.md](docs/USERSTORIES.md): functional requirements.
- [docs/RELEASING.md](docs/RELEASING.md): CI/CD and release process.
- [docs/MAINTENANCE.md](docs/MAINTENANCE.md): five year maintenance strategy.
- [CONTRIBUTING.md](CONTRIBUTING.md): how to contribute (Conventional Commits).

## Contributing

Contributions are welcome. Please read [CONTRIBUTING.md](CONTRIBUTING.md). Commits follow
[Conventional Commits](https://www.conventionalcommits.org/en/v1.0.0/); releases are automated with
release-please, which attaches the built jar to the GitHub release and uploads a CycloneDX SBOM.

## License

SkyGrid-ng is licensed under the GNU Affero General Public License v3.0. See [LICENSE](LICENSE).
