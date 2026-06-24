# SkyGrid-ng

A chunk generator plugin for Paper 26.1 that revives the classic **SkyGrid** and also ships an
adjacency based **Wave Function Collapse** surface generator. Pure Java 25, hexagonal
architecture, i18n through Adventure, JSON configuration, switchable at runtime via `/skygrid`.

## Algorithms

Selected by `algorithm` in `generator.json`, switchable in game with `/skygrid algorithm <id>`:

- **`skygrid`** (default): classic SkyGrid. One block on every grid point (grid length `spacing`,
  default 4), air between, floating in the void. Each cell is sampled independently from a
  weighted table by a coordinate hash. O(1) per cell, parallel, fastest. Faithful to SethBling's
  2012 SkyGrid.
- **`noise_skygrid`**: same grid, but block choice follows a seed based value noise field so
  similar blocks cluster into veins and themed regions. Also O(1) per cell, much faster than WFC.
- **`wfc`**: the adjacency constrained surface generator with seamless regions and in game editable
  rules.

All algorithms are seed based and deterministic.

## Quick start

1. Put `skygrid-ng.jar` in `plugins/` and start the server once to generate
   `plugins/SkyGrid-ng/generator.json`.
2. Add a world using the generator in `bukkit.yml`:

   ```yaml
   worlds:
     skygrid:
       generator: SkyGrid-ng
   ```

3. Load the world and play. See [docs/ADMIN.md](docs/ADMIN.md) for all settings, commands, and
   tuning advice for large servers (around 100 players).

## Build

```bash
./gradlew build                    # compile and test every module
./gradlew :platforms:paper:runServer # start a Paper 26.1 test server
```

The shaded plugin jar is at `platforms/paper/build/libs/skygrid-ng-<version>.jar`. The OneLiteFeather
network distribution is `platforms/paper-olf/build/libs/skygrid-ng-olf-<version>.jar`. SkyGrid-ng is a
multi-module project (`api`, `ports`, `core`, `adapters/*`, `platforms/*`); see
[docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).

## Documentation

- [docs/ADMIN.md](docs/ADMIN.md): all configuration, commands, and performance tuning.
- [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md): hexagonal design and extension points.
- [docs/USERSTORIES.md](docs/USERSTORIES.md): functional requirements.
- [docs/RELEASING.md](docs/RELEASING.md): CI/CD and release process.
- [docs/MAINTENANCE.md](docs/MAINTENANCE.md): five year maintenance strategy.
- [CONTRIBUTING.md](CONTRIBUTING.md): how to contribute (Conventional Commits).

## Contributing

Contributions are welcome. Please read [CONTRIBUTING.md](CONTRIBUTING.md). Commits follow
[Conventional Commits](https://www.conventionalcommits.org/en/v1.0.0/); releases are automated with
release-please and published to Modrinth and Hangar.

## License

SkyGrid-ng is licensed under the GNU Affero General Public License v3.0. See [LICENSE](LICENSE).
