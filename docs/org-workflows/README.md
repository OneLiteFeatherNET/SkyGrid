# Proposed org workflow

`gradle-plugin-publish.yml` is meant to live in `OneLiteFeatherNET/workflows` next to the existing
`gradle-publish.yml` (which targets Maven). It publishes a Paper plugin to Modrinth and Hangar,
attaches the jar to the GitHub release, and uploads a CycloneDX SBOM to Dependency-Track.

## How to adopt

1. Add this file to `OneLiteFeatherNET/workflows/.github/workflows/gradle-plugin-publish.yml` and
   tag a release (for example `v2.4.0`).
2. In each plugin repository, call it from the release pipeline (see SkyGrid-ng's
   `.github/workflows/release-please.yml`):

   ```yaml
   publish:
     needs: release-please
     if: needs.release-please.outputs.release_created == 'true'
     uses: OneLiteFeatherNET/workflows/.github/workflows/gradle-plugin-publish.yml@v2.4.0
     with:
       java-version: "25"
       tag-name: ${{ needs.release-please.outputs.tag_name }}
       project-name: "SkyGrid-ng"
       project-version: ${{ needs.release-please.outputs.version }}
     secrets: inherit
   ```

Plugin specific publishing details (Modrinth project id, Hangar slug, supported Minecraft
versions) stay in each plugin's `build.gradle.kts`, so the workflow has no per project logic.
