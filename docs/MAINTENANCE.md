# Five Year Maintenance Strategy

A plan to keep SkyGrid-ng healthy and cheap to maintain as an open source OneLiteFeather project.
The guiding principle is automation first: humans review and decide, machines build, release, and
update.

## Pillars

1. **Automation over process.** release-please, Renovate, reusable org workflows, and CI checks
   carry the routine load. A maintainer's job is review and direction, not mechanics.
2. **A pure, tested core.** The hexagonal core stays framework free so it survives Paper API
   churn. When Minecraft breaks the world, only the adapter layer should need work.
3. **Small, conventional commits.** Every change is releasable and traceable.
4. **Visible supply chain.** CycloneDX SBOM plus Dependency-Track gives early warning on
   vulnerable dependencies.

## Year by year

### Year 1: foundation and adoption
- Add MockBukkit integration tests for the adapter layer (generation, command, populator).
- Add SkyGrid loot tables and configurable spawner types (most requested gap vs the classic map).
- Establish a release cadence: ship whenever the Release PR has meaningful changes, at least
  monthly while active.
- Set up bStats and a Crowdin project; recruit translators.

### Year 2: scale and platforms
- Folia support: regionize the generator and any scheduling; SkyGrid is already stateless and a
  good Folia candidate. Gate behind capability detection like AntiRedstoneClock does.
- Per dimension SkyGrid (overworld, nether, end style grids) with separate block tables.
- Performance pass for 100+ player servers; publish a tuned reference preset and a `/spark` based
  tuning guide.
- Promote the plugin publish steps into a reusable `OneLiteFeatherNET/workflows` workflow so other
  plugins share it.

### Year 3: ecosystem and API
- Stable public API module (`skygrid-ng-api`) so other plugins can register block tables, custom
  algorithms, or post processors. Keep it a separate published artifact.
- WorldGuard / region plugin awareness if demand exists (the AntiRedstoneClock multi version
  support modules are the template for optional integrations).
- Consider a fourth algorithm (Wang/corner tiles or Model Synthesis) once the API exists.

### Year 4: hardening and longevity
- Track two Minecraft major versions at a time; drop the oldest each year.
- Annual dependency and license audit (SBOM driven). Review the AGPL choice against community
  needs.
- Documentation refresh; ensure every config key and command is documented and tested.

### Year 5: handover readiness
- Ensure at least two active maintainers and current CODEOWNERS.
- Verify the project is fully reproducible from a clean clone with no tribal knowledge.
- Re-evaluate the architecture against the then current Paper APIs; refactor the adapter layer if
  needed, leaving the core intact.

## Minecraft version policy

- Target the current Paper major; support the previous major for one cycle.
- The pure core does not depend on Paper, so version updates touch only the adapter modules
  (`plugin`, `plugin`, ...), never `api`/`ports`/`core`.
- Paper's first fully deobfuscated era (26.1+) reduces breakage risk; still pin the Paper build in
  the version catalog and let Renovate propose bumps.

## Dependency policy

- Renovate (org preset) opens grouped dependency PRs; CI must pass before merge.
- Security advisories from Dependency-Track are triaged within one week.
- Keep runtime dependencies minimal (currently Jackson, Incendo Cloud, and Guice, all relocated).
  Prefer the platform (Adventure, Paper) over new libraries.

## Bus factor and governance

- CODEOWNERS requires review from `skygrid-ng-maintainers`; `core-team` owns `.github`.
- All decisions of consequence (license, breaking changes, new platforms) are recorded in an ADR
  style note under `docs/decisions/` (to be started in year 1).
- Issues and the project board are the single source of truth; no private backlog.

## What "low maintenance" means in practice

A quiet month should require zero manual action: Renovate updates dependencies, CI verifies,
release-please proposes a release, and a maintainer merges the Release PR when ready. If a quiet
month needs manual work, that is a bug in the process and should be automated away.
