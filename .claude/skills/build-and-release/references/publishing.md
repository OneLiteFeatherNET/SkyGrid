# Publishing and CI

Details for the release pipeline, the reusable org workflows, and the placeholders to fill before
the first public release.

## Reusable OneLiteFeather workflows

This repo calls shared workflows from `OneLiteFeatherNET/workflows`, pinned by tag:

- `.github/workflows/build-pr.yml` -> `gradle-build-pr.yml@v2.3.0` (builds and tests PRs).
- `.github/workflows/close-invalid-prs.yml` -> `close-invalid-prs.yml@v2.3.0`.
- `.github/workflows/release-please.yml` runs release-please inline, then a `publish` job that calls
  `gradle-plugin-publish.yml@v2.4.0` with `secrets: inherit`, passing `project-name: "SkyGrid-ng"`,
  the tag, and the version. The publish job only runs when release-please created a release.

The publish workflow (`gradle-plugin-publish.yml@v2.4.0`) does not exist in the org repo yet. A
proposed implementation is in `docs/org-workflows/gradle-plugin-publish.yml` with a README. It must
be added to `OneLiteFeatherNET/workflows` and tagged `v2.4.0` before the first release publishes.
It runs Modrinth + Hangar uploads and generates plus uploads the CycloneDX SBOM to Dependency-Track.

## Secrets the publish workflow needs

Set these as org or repo secrets (the publish job uses `secrets: inherit`):

- `MODRINTH_TOKEN`
- `HANGAR_API_TOKEN`
- `DEPENDENCYTRACK_HOSTNAME` (optional; the SBOM upload step is skipped when empty)
- `DEPENDENCYTRACK_APIKEY`

## Placeholders to fill before the first publish

- `build.gradle.kts` Modrinth `projectId = "REPLACE_WITH_MODRINTH_PROJECT_ID"`: set to the real
  Modrinth project id or slug after creating the project.
- `build.gradle.kts` Hangar `id = "SkyGrid-ng"`: confirm this matches the Hangar project slug after
  creating it on hangar.papermc.io.
- `supportedMinecraftVersions = listOf("26.1")` and `minecraftVersion("26.1.2")`: extend when
  supporting more Minecraft versions.
- `mockbukkit-v26.1.2` in the version catalog: confirm the published MockBukkit artifact for the
  Paper line before the first CI test run; Renovate keeps it current afterwards.

## Outputs

- Shaded jar: `build/libs/skygrid-ng-<version>.jar` (the `shadowJar` task).
- SBOM: `build/reports/cyclonedx/bom.{xml,json}` from the aggregate `cyclonedxBom` task; CI uploads
  the XML to Dependency-Track.

## release-please mechanics

- Config: `release-please-config.json` (`release-type: java`, `package-name: skygrid-ng`,
  `bump-minor-pre-major: true`). Manifest: `.release-please-manifest.json`.
- The version in `gradle.properties` carries the marker comment `# x-release-please-version`. Do not
  edit it by hand; release-please updates it in the release PR.
- A normal flow: merge Conventional Commits to `main` -> release-please opens or updates a release
  PR -> merging that PR tags the release and triggers the publish job.

## Sanity checks before tagging a release

- `./gradlew build` passes (compiles, shades, runs tests).
- `./gradlew runServer` boots and a world generated with `generator: SkyGrid-ng` produces terrain.
- The CHANGELOG entry that release-please generated reads correctly.
- No `run/` directory or other local artifacts are staged.
