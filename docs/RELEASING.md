# Releasing and CI/CD

This document describes how GridWFC manages Actions, releases, and project tracking. It is the
go forward standard for the repository.

## Overview

GridWFC uses **Conventional Commits** plus **release-please** for fully automated, low touch
releases, and publishes to **Modrinth** and **Hangar** with a **CycloneDX** SBOM for supply chain
tracking. This mirrors the newest OneLiteFeather setup (Otis) and the publishing targets of the
plugin projects (AntiRedstoneClock-Remastered).

```
commit (feat/fix) -> push to main -> release-please opens a Release PR
        -> merge Release PR -> tag + GitHub release -> publish job runs
        -> Modrinth + Hangar upload, jar attached to the GitHub release, SBOM to Dependency-Track
```

## Pipelines

| Workflow | Trigger | What it does |
|----------|---------|--------------|
| `build-pr.yml` | pull_request | Reuses `OneLiteFeatherNET/workflows/gradle-build-pr.yml`: builds on Linux/Windows/macOS, runs tests, publishes test and coverage reports. |
| `release-please.yml` | push to `main` | Runs release-please. On a merged Release PR it tags, creates the GitHub release, then the `publish` job uploads to Modrinth and Hangar and the SBOM to Dependency-Track. |
| `close-invalid-prs.yml` | pull_request_target | Reuses the org workflow to close PRs opened from `main`. |

We reuse the central `OneLiteFeatherNET/workflows` repository wherever possible so pipeline
maintenance happens once for all projects.

## release-please

- `release-please-config.json` uses `release-type: java`, `include-v-in-tag: true`.
- The version lives in `gradle.properties` (`version = x.y.z # x-release-please-version`) and is
  bumped automatically; never bump it by hand.
- `.release-please-manifest.json` tracks the last released version.
- `CHANGELOG.md` is generated from commit messages.

## How a human cuts a release

There is no manual release step. The flow for a maintainer is:

1. Merge feature and fix PRs with Conventional Commit titles.
2. release-please keeps an open "chore(main): release x.y.z" PR up to date. Review the proposed
   changelog and version.
3. Merge that Release PR when you want to ship. The tag, GitHub release, Modrinth and Hangar
   uploads, and SBOM happen automatically.
4. To ship a prerelease, target a `next`, `beta`, or `alpha` branch (optional, add later).

## Required repository secrets

| Secret | Used for |
|--------|----------|
| `MODRINTH_TOKEN` | Modrinth upload |
| `HANGAR_API_TOKEN` | Hangar upload |
| `DEPENDENCYTRACK_HOSTNAME`, `DEPENDENCYTRACK_APIKEY` | SBOM upload (optional) |
| `GITHUB_TOKEN` | provided by Actions |

Set these as organization secrets so every OneLiteFeather plugin inherits them.

## One time setup checklist

- [ ] Create the Modrinth project, set its id in `build.gradle.kts` (`modrinth.projectId`).
- [ ] Create the Hangar project, set its slug in `build.gradle.kts` (`hangarPublish ... id`).
- [ ] Create the GitHub teams `gridwfc-maintainers` and ensure `core-team` exists (CODEOWNERS).
- [ ] Add the repository to the OneLiteFeather Renovate config and a GitHub Project board.
- [ ] Configure Crowdin for `messages/Bundle_en.properties`.
- [ ] Add branch protection on `main`: require `build-pr` checks and one CODEOWNER review.

## Project tracking

Issues use the `status:` and `type:` label scheme and are added to a GitHub Project board, as in
the other OneLiteFeather repositories. Triage assigns `status: needs triage` first, then a
maintainer moves it across the board. Keep this lightweight; the board is the single source of
truth for what is in flight.

## Versioning policy

Semantic Versioning, derived from commit types. Breaking config or API changes require a major
bump and a migration note in the changelog body and in `docs/ADMIN.md`.
