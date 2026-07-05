# The SkyGrid-ng agent team

SkyGrid-ng is built and designed by a fleet of specialized Claude Code subagents defined in
`.claude/agents/`, instead of one generic agent doing everything. Each agent owns a role, has
scoped tool access, and preloads the project skill(s) it needs. This document is the roster; the
individual `.claude/agents/*.md` files are the source of truth for exact scope and rules.

Every agent file ends with a shared **Modern Java 25 house style** section, so any code a member
writes or reviews leans on current language features (records, sealed types with exhaustive
switches, pattern matching, text blocks, sequenced collections, virtual threads for blocking I/O
only) while keeping the project's hard limits (seed determinism, framework-free core, no em dashes).

## Why a team instead of one agent

This follows the architecture patterns from Anthropic's
["Building Effective AI Agents"](https://www.anthropic.com/research/building-effective-agents) and
["How we built our multi-agent research system"](https://www.anthropic.com/engineering/multi-agent-research-system):

- **Routing**: each agent's `description` tells Claude when to delegate to it, so a request for
  "add loot to chests" reaches `domain-config-specialist`/`paper-adapter`, not a generic worker.
- **Orchestrator-workers**: `producer` (or the main session) breaks a cross-cutting request into
  per-role pieces and delegates each one.
- **Parallelization (sectioning)**: independent pieces that touch different modules or roles (for
  example a new mechanic's design rationale, its config encoding, and its Java wiring) can run as
  separate agents at the same time.
- **Focused system prompts beat one shared one**: a narrow role with its own rules (what it owns,
  what it hands off, what it must never do) is more reliable than a single prompt trying to hold
  every rule at once.

## Roster

### Engineering
Builds and maintains the plugin itself. `core-architect` and `paper-adapter` are each the general
purpose Java developer for their layer, not narrowly limited to the examples in their description;
route anything else in that layer to them too. See `CLAUDE.md` for the module layout.

| Agent | Owns |
|---|---|
| `core-architect` | General purpose developer for `api/`, `ports/`, `core/`: algorithms, solver, use case services, `RuleState` |
| `paper-adapter` | General purpose developer for `adapters/*`, `platforms/common`: Bukkit/Guice wiring, the `/skygrid` command, plugin lifecycle |
| `test-engineer` | JUnit 5 + AssertJ + Mockito + MockBukkit tests across every module |
| `release-engineer` | Gradle build, version catalog, CI workflows, CycloneDX publishing |
| `architecture-reviewer` | Read only. Hexagonal dependency rule, ArchUnit, CONTRIBUTING conventions |
| `security-reviewer` | Read only. Generation resource exhaustion, command permission checks, malformed-input handling, dependency supply chain (CycloneDX) |
| `security-specialist` | Implements the fixes `security-reviewer` finds: permission checks, input validation, secrets handling, CVE bumps |
| `java-specialist` | Cross-cutting Java 25/JVM advisor, consulted by `core-architect`/`paper-adapter`; translates Jakarta/Java EE concepts (CDI, Bean Validation, JPA) into this codebase's actual idioms |
| `paper-platform-specialist` | Read-mostly. Deep Paper API/version-migration advisor (Folia regionization, scheduler internals) |
| `minecraft-platform-specialist` | Read-mostly. Platform-neutral Minecraft domain knowledge, Fabric/Forge/Sponge portability assessment for `core` |

### Design and product
Decides what the game mode is and why, grounded in `docs/USERSTORIES.md` and player motivation
frameworks. See Epic F there for the current player engagement hypotheses.

| Agent | Owns |
|---|---|
| `game-designer` | Core loop, mechanics, progression, difficulty curve (MDA framework) |
| `player-psychology-designer` | Player motivation and engagement (Self-Determination Theory, Octalysis, Bartle types, flow); guards against dark patterns |
| `domain-config-specialist` | `generator.yaml`/`generator.conf` schema, presets, block tables, WFC tiles: encodes decisions others make |
| `narrative-worldbuilding-designer` | Preset theming, flavor text, biome-to-theme coherence |
| `interface-ux-designer` | Chat message and log copy, channel choice (chat/action bar/boss bar/scoreboard); boss bar and scoreboard not built yet |
| `accessibility-designer` | Color-vision-safe block choices, comfort/difficulty preset variants, non-color-only feedback (Epic G) |
| `technical-artist` | Particle/sound polish on vanilla assets, future resource pack scoping. Thin scope today by design, no custom assets exist yet |
| `playtest-qa-coordinator` | Human playtesting, turning play sessions into balance feedback and sharper acceptance criteria |
| `producer` | Roadmap, prioritization, cross-role coordination, keeps `docs/USERSTORIES.md` current |

### Data and analytics
| Agent | Owns |
|---|---|
| `live-ops-analyst` | Self-hosted, opt-in analytics on preset/algorithm usage (Epic H); turns real data into evidence for the design roles. Not a third party service, privacy rules (data minimization, bounded retention) are non-negotiable |
| `postgresql-specialist` | Implements `live-ops-analyst`'s schema/migrations/queries and adapter code in PostgreSQL. No PostgreSQL exists in this repo yet, this agent builds it from scratch |

### Infrastructure and operations
Thin scope today by design, same honesty pattern as `technical-artist`: these agents say plainly
when there's nothing concrete to do rather than inventing infrastructure. `devsecops-specialist` is
the exception, it has real work today against the existing CycloneDX pipeline.

| Agent | Owns |
|---|---|
| `devops-specialist` | Operating infrastructure beyond build/publish (test-environment automation, observability). No deployed service exists yet |
| `devsecops-specialist` | Automates security into CI (SBOM gates, dependency-review bots, secret scanning). Real work today via CycloneDX/Dependency-Track |
| `cloudnet-specialist` | Future CloudNet integration for `platforms/paper-olf`. No CloudNet code exists in this repo yet |
| `kubernetes-specialist` | Future container/Kubernetes deployment artifacts (Agones/Shulker style). No Docker/Kubernetes surface exists in this repo yet |

### Community
| Agent | Owns |
|---|---|
| `community-manager` | Triages GitHub issues/feedback into scoped proposals for the rest of the team |

## How a cross-cutting feature typically flows

A new mechanic (say, a new decoration type) usually moves through the team like this:

1. `game-designer` (with input from `player-psychology-designer` and, where relevant,
   `accessibility-designer`) decides what it should do and why, and writes it up as a story/
   acceptance criteria in `docs/USERSTORIES.md`.
2. `domain-config-specialist` encodes the resulting values into the config schema.
3. `core-architect` and `paper-adapter` implement it (often in parallel once the port shape is
   agreed, consulting `java-specialist`/`paper-platform-specialist`/`minecraft-platform-specialist`
   for anything needing deeper language or platform expertise), `test-engineer` covers it,
   `technical-artist` adds any particle/sound polish, `interface-ux-designer` writes any new
   command or log message copy for `paper-adapter` to wire in.
4. `architecture-reviewer` and `security-reviewer` check the diff before it ships; `security-specialist`
   fixes anything they flag.
5. `playtest-qa-coordinator` validates it plays as intended; `live-ops-analyst` (built on
   `postgresql-specialist`'s persistence adapter) tracks how it's actually used post-release.
   Findings from either loop back to step 1 if the design doesn't hold up.

Not every change needs every step. A one-line config tweak doesn't need a design review; a pure
bugfix doesn't need a playtest. Use the smallest slice of the team the task actually needs, and
don't invoke the thin-scope infrastructure roles (`devops-specialist`, `cloudnet-specialist`,
`kubernetes-specialist`) unless the task actually requires them, they exist for when real
infrastructure work arrives, not by default.
