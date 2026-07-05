# Publishing and CI

Details for the release pipeline and the reusable org workflows.

## Reusable OneLiteFeather workflows

This repo calls shared workflows from `OneLiteFeatherNET/workflows`, pinned by tag:

- `.github/workflows/build-pr.yml` -> `gradle-build-pr.yml@v2.3.0` (builds and tests PRs).
- `.github/workflows/close-invalid-prs.yml` -> `close-invalid-prs.yml@v2.3.0`.
- `.github/workflows/release-please.yml` runs release-please inline, then a `publish` job that calls
  `gradle-plugin-publish.yml@v2.4.0` with `secrets: inherit`, passing `project-name: "SkyGrid-ng"`,
  the tag, the version, and `publish-task: "cyclonedxBom"`. The publish job only runs when
  release-please created a release.

SkyGrid-ng does not publish to Modrinth or Hangar (OneLiteFeather-internal use only), so it
overrides the reusable workflow's default `publish-task` to run only the CycloneDX SBOM task
through Gradle. The workflow's jar-attach-to-GitHub-release and SBOM-upload-to-Dependency-Track
steps are not gated by `publish-task` and continue to run as usual.

## Secrets the publish workflow needs

Set these as org or repo secrets (the publish job uses `secrets: inherit`):

- `DEPENDENCYTRACK_HOSTNAME` (optional; the SBOM upload step is skipped when empty)
- `DEPENDENCYTRACK_APIKEY`

## Outputs

- Shaded jar: `platforms/paper/build/libs/skygrid-ng-<version>.jar` (the `shadowJar` task), attached
  to the GitHub release.
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
