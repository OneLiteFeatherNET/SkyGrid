# Design

## Context

SkyGrid-ng has no per-player persistence yet. The season work (`add-season-lifecycle`) provides season ids,
the current season and `ProgressScope`, and the Flyway fix gives each persistence adapter its own migration
folder and history table. The shop, mailbox and market changes build on the ledger defined here. See
proposal.md for motivation and the product decisions in `docs/design/economy.md` section 6.

Constraints that shape the approach:
- `api`, `ports` and `core` stay framework free; PostgreSQL access lives in `adapters/economy`.
- Money must never pass through `double`; Vault calls use `double` and are converted exactly at the edge.
- Vault is synchronous and is called on the server main thread by other plugins.

## Goals / Non-Goals

**Goals:**
- A ledger in which every coin movement is one balanced, idempotent, auditable transaction.
- Atomic pay, audited admin adjustments and currency rewards that survive crashes and retries.
- Vault compatibility for shop, rank and placeholder plugins without new dependencies at runtime.

**Non-Goals:**
- Items, mailbox, shops and market (separate changes; the ledger is designed to be reused by them).
- Fractional coins, multiple currencies, bank accounts and any real-money path.
- Multi-server balance consistency (one server per database in v1).
- A balance leaderboard (deferred, see I5 item 3).

## Decisions

**Money as `long` minor units with zero fractional digits.** `Money(long minor)` is validated in its compact
constructor and uses `Math.addExact`, `subtractExact` and `multiplyExact`. The fractional digit count is fixed
at 0 for v1 because changing it later rescales every stored row. The design draft carried an
`EconomySettings.fractionalDigits` field and a config key; both are dropped, and the parser rejects any
non-whole input. Alternatives: `BigDecimal` (rejected: scale drift and no DB type match), JSR 354 (rejected in
the Challenge and Season skill: it pulls runtime dependencies that break the framework-free core).

**Double-entry ledger in PostgreSQL, tables `economy_account`, `economy_transaction`, `economy_posting`.**
Each transaction has legs that sum to zero, so the sum over all accounts is always zero and `audit` can
verify it. System accounts (`system:mint`, `system:shop`, `system:market_tax`) may go negative; player accounts
may not. Alternative: a single balance column with an event log (rejected: the sum invariant cannot be
checked independently of the balance it is meant to check).

**Posting algorithm.** One READ COMMITTED transaction: insert the transaction row with `ON CONFLICT
(idempotency_key) DO NOTHING` (zero rows means already applied), lock the involved accounts with
`SELECT ... ORDER BY account_id FOR UPDATE` (fixed order prevents deadlocks), compute new balances in Java
with exact arithmetic, check the player bounds, then update accounts and insert postings with
`balance_after`. The adapter retries up to three times on SQLState `40P01` or `40001`; the key makes the
retry safe.

**Idempotency keys.** Each command invocation generates its key once (`pay:<uuid>`), so a retry after a
deadlock never applies twice. Starting balances use `start:<seasonId>:<playerId>`, season resets use
`season-reset:<seasonId>:<accountId>`, currency rewards use `task:<seasonId>:<challengeId>:<taskId>:<playerId>`
and each Vault call generates `vault:<uuid>` once, before its first attempt, so a retried call never applies twice.

**Season scope.** Every account, transaction and posting carries the season id; the primary key of an account
is `(season_id, account_id)`. A new season starts with fresh accounts, so the reset at season end only has
to zero the ending season's balances. The reset posts against `system:mint` so the sum stays zero. Season
transitions call the reset through the season port defined by `add-season-lifecycle`.

**Migration scope.** The economy design draft put all tables in one `V1__create_economy.sql`. This change
creates only the three ledger tables in `V1`. The mailbox and shop intent tables arrive in `V2` with
`add-admin-shop`, and the market table in `V3` with `add-player-market`, so every PR ships only the tables it
uses and merges on its own.

**Pay and admin commands.** `WalletService` validates the amount, applies a Clock-based per-player cooldown
(`CooldownTracker`, evicted on quit) and builds the postings: pay debits the payer and credits the recipient;
give debits `system:mint` and credits the player; take is the reverse; set posts the difference. Balance
rejections never clamp.

**Currency rewards.** `Reward.CurrencyGrant(Money amount)` is added to `api.model`. `RewardGrantor#grant`
receives a `RewardKey` so the currency branch can build its key. The credit runs on the economy executor, not
the main thread. `CurrencyRewardReconciler` runs on join, asks `TaskProgressUseCase#isCompleted` for each
configured currency task and re-posts with the same key; `AlreadyApplied` is the normal outcome.

**Vault provider.** The Vault classes live only in `platforms.common.economy.vault`. `EconomyFeature` checks
`Class.forName` for the legacy Economy class before it instantiates `VaultRegistrar`, so a server without Vault
never loads Vault types. The legacy provider and, when present, the VaultUnlocked provider register through
`ServicesManager` at the configured priority (default `Normal`) and unregister on disable. Reads (`getBalance`,
`has`) may be served from a `BalanceCache` refreshed after each committed posting and loaded on join, with a
5 s TTL. Writes (`withdraw`, `deposit`) always go to the database on the calling thread and re-check the
balance under the row lock; a write over 50 ms logs a warning. Bank and multi-currency methods report
unsupported.

**Commands and permissions.** `/balance` (alias `/bal`), `/pay`, `/skygrid eco give|take|set|history|audit`.
The root labels `/balance`, `/bal` and `/pay` sit behind `economy.commands.rootLabels` (default on, as
decided in `economy.md` section 6.2). Permissions as in the proposal; every handler declares its permission
so tab completion is gated.

**Threading.** `EconomyExecutor` is a bounded pool (default 4 threads) created by `EconomyModule` and shut down
with a bounded wait in `onDisable`. Command handlers return `CompletableFuture<Void>` built on the executor they
receive, which keeps `adapters/command` framework free. A per-player in-flight set drops repeated commands from
the same player.

**Configuration.** A new `economy` section: currency singular and plural names, `startingBalance` (default 0),
`maxBalance` (default 1000000000000 coins), `pay.cooldown` (default 2 s), `pay.maxAmount`, `vault.enabled`,
`vault.priority`. Amounts are whole-coin strings parsed by the same parser as the commands. Market and shop keys
are added by their own changes.

## Risks / Trade-offs

- [Lost update under concurrent transfers] → fixed lock order, balances recomputed under the lock, Testcontainers
  race test with 200 transfers from 16 threads.
- [Double credit on retry or double click] → unique idempotency key, per-command key, in-flight guard.
- [Admin or plugin minting money without a trace] → every give, take, set and Vault write is a ledger row with
  actor and reason; `history` and `audit` expose them. A malicious plugin with Vault access can still mint;
  this is accepted and documented as server policy.
- [Vault cache returns a stale balance to a placeholder] → cache is read-only and TTL bound; writes never use it.
- [Main-thread stalls from Vault writes] → single-row primary-key updates; slow writes are logged at 50 ms.
- [Vault class missing at runtime] → Vault types referenced only by `VaultRegistrar`, which is loaded only when the
  Vault class is present; a bootstrap test covers the no-Vault start.
- [Abuse: pay spam or harassment] → cooldown, maximum amount, recipient notified only on success.
- [Abuse: pay to a name that never joined] → recipient must have an account; no ghost accounts are created.
- [Abuse: SQL injection] → JPA parameter binding only, as in the other Postgres adapters.
- [Abuse: overflow] → `long` minor units, exact arithmetic, DB `CHECK` constraints, `maxBalance`.

## I5 dark-pattern review (challenge-season-system, items 1 to 8)

1. **Drives.** Accomplishment (completing tasks earns coins) and ownership (a visible balance). No randomness, no
   crate or gambling sink, no balance decay and no upkeep. Any future lottery is a new I5 review.
2. **Transparency.** Fixed prices and amounts only; `/pay` confirms the amount and the balance after it; admin
   `set` shows the delta before it is confirmed. No hidden fees in this change.
3. **Bartle.** Achievers (earning), socializers (paying). The balance leaderboard is deferred because it rewards
   hoarding; the leaderboard is not part of this change.
4. **Adaptive rewards.** None. Currency reward amounts are static configuration and never react to struggle or
   disengagement.
5. **Late joiners.** A persistent balance would compound early advantage, so balances are season scoped and reset
   at season end with an audited `SEASON_RESET`; a new season starts everyone at the starting balance.
6. **Outside observer.** The plugin has no path that sells coins for money, and the README states that. Vault lets
   other plugins do so; that is server policy, documented in the README.
7. **Metrics for `playtest-qa-coordinator`.** Balance Gini per week (fail above 0.8), share of active players with a
   trade, median coins of first-week players against the server median, and `audit` mismatch count (must be 0).
8. **Timers.** The pay cooldown is a 2 s anti-spam guard, not a countdown pressure on the player. No timed sale
   exists in this change.

## Migration Plan

- Deploy order: `add-season-lifecycle` and the Flyway fix first; then this change. The economy adapter's
  migrations apply on first start and create only the three ledger tables.
- Rollback: redeploy the previous jar. The economy tables stay in place, the older build does not read them, and
  ledger rows are never deleted by a rollback.
- The README statement ships in the same change.
