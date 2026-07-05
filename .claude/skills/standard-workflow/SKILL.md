---
name: standard-workflow
description: >
  The mandatory chain-of-thought for every single prompt in this repository, with no exception for
  size or triviality. Defines the nine phases from raw prompt to result summary: derive
  requirements, get them approved, survey the current repo state, research external sources (web,
  DeepWiki, Context7), draft an implementation plan, get the plan approved, implement it,
  test it (deciding per change whether ArchUnit coverage is warranted), and report a result
  overview. Also defines the spelling/clarity check that runs before phase 1, and the rule that the
  `producer` agent is always the first hop for delegated work. Read this at the start of every
  turn, not just for large features.
---

# Standard workflow (chain-of-thought)

This is the required reasoning shape for every prompt handled in this repository, from a one-line
question to a multi-module feature. Skipping a phase because a request "looks simple" is not a
valid reason to skip it: the user has deliberately chosen exhaustiveness over speed here.

The phases below are written for the top-level session (the one holding the interactive tools:
`AskUserQuestion`, `ExitPlanMode`, `EnterPlanMode`). Subagents (via the `Agent` tool) do not talk to
the user directly; they do the legwork for phases 3, 4, 5, 7, and 8, and report back so the
top-level session can run the approval gates in phases 2 and 6 itself.

## Phase 0: Spelling and clarity check

Before interpreting intent, scan the user's prompt for spelling errors, typos, or ambiguous
phrasing. If any are found, do not silently guess: restate your corrected understanding of the
prompt back to the user (a short paraphrase, not a wall of text) and get explicit confirmation
before moving to phase 1. Use `AskUserQuestion` when there is a genuine fork in meaning; a plain
one-line confirmation is enough when the correction is unambiguous.

## Phase 1: Derive requirements

From the (now clarified) prompt, write out the concrete requirements: what must be true when this
is done. Keep it to the smallest set that captures the ask, don't invent scope the user didn't ask
for.

## Phase 2: Requirements approval

Show the requirements to the user before doing any repo investigation or planning. Use
`AskUserQuestion` for anything with more than one reasonable reading; a short confirmation message
is enough when the requirements are a direct restatement of an unambiguous prompt. Do not proceed
to phase 3 without an explicit go-ahead.

## Phase 3: Current state

Investigate what already exists in the repo relevant to the approved requirements: code, config,
tests, docs, and the matching skill(s) from `.claude/skills/`. Route this through the `producer`
agent (see "Agent team as standard" below) rather than reading everything inline yourself, unless
the investigation is a single, obvious file lookup.

## Phase 4: External research

Look for authoritative external sources relevant to the requirements before designing a solution:
web search, `mcp__deepwiki__*` for how comparable open source projects solved the same problem, and
`mcp__claude_ai_Context7__*` for current library/framework documentation (Paper API, Jackson,
Guice, etc). Skip a source category only when it is genuinely not applicable (for example, a pure
internal refactor with no external API surface), and say so explicitly rather than silently
omitting it.

## Phase 5: Implementation plan

Turn the requirements plus phases 3 and 4's findings into a concrete plan: files/modules touched,
the shape of the change, and how it satisfies each requirement from phase 1. Use `EnterPlanMode` /
the `Plan` agent for this when the change is non-trivial.

## Phase 6: Plan approval

Present the plan to the user (`ExitPlanMode` or an explicit confirmation message) and incorporate
their feedback before writing any code. Do not start phase 7 without an explicit go-ahead; if
feedback changes the plan materially, loop back through phase 5 once rather than patching the plan
live.

## Phase 7: Implementation

Implement the approved plan, folded together with the user's phase 6 feedback. Delegate to the
specialist agent(s) that own the touched layer (see the agent list in `.claude/agents/`) via
`producer`, rather than writing cross-cutting code inline, unless the change is confined to a
single file already open in context.

## Phase 8: Tests

Write the tests the change needs: unit/implementation tests for the new or changed logic,
integration tests where a port/adapter boundary is exercised, and decide explicitly whether ArchUnit
coverage in `:verification` is warranted for this change. ArchUnit is warranted when the change:

- adds or moves a package across the `api`/`ports`/`core`/`adapters`/`platforms` boundary,
- introduces a new port, adapter, or module,
- changes an existing dependency direction, or
- could plausibly leak a framework type (Bukkit, Jackson, Guice, jakarta.inject) into a
  framework-free layer.

ArchUnit is not warranted for a same-module logic change, a config value tweak, a doc-only change,
or a test-only change: say explicitly that it was considered and why it's skipped, rather than
omitting the decision silently. See the `testing` skill for the black-box/white-box split and
fixture conventions.

## Phase 9: Result summary

Close with a concise result overview: what changed, which requirements from phase 1 it satisfies,
what was tested (and what wasn't, if anything), and what's left or what to watch for.

## Agent team as standard

For every prompt, not only cross-cutting ones, route delegated work through the `producer` agent
first. `producer` decides whether to delegate straight to a single specialist (its existing
behavior when a request clearly belongs to exactly one owner) or fan out across the team; either
way it is the standard entry point, not a fallback for when nobody else was asked. The top-level
session still owns phases 0, 2, and 6 directly since those require talking to the user.
