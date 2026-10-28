# Proposal

Ships under the Conventional Commits type and scope `feat(economy)`.

## Why

SkyGrid rewards players only with challenge progress and items, so there is no shared currency for
trade, payment or server-run rewards. Server owners also need to pay out and correct coins without
losing track of where money came from, and plugins that expect Vault should see the currency.
This change ships the minimum shippable economy (epic J1, delivery slices 17 to 21 and 25): a
season-scoped coin ledger with pay, audited admin adjustments, a currency task reward, a Vault
provider and a ledger integrity check.

## What Changes

- Add `Money` to the API model: whole coins held as a non-negative count of minor units in a `long`.
  The fractional digit count is fixed at 0 for v1 and is not configurable.
- Add the wallet ports and `WalletService` in core. Every amount is validated before a posting is
  built, and no `double` is used in core.
- Add `adapters/economy` with its own Flyway folder `db/migration/economy`, its own history table
  `flyway_history_economy` and an append-only double-entry ledger in PostgreSQL (accounts,
  transactions, postings). Only the wallet tables are created by this change.
- Add `/balance` (alias `/bal`), which also shows the date the balance resets, `/pay <player> <amount>` and
  the audited admin commands `/skygrid eco give|take|set|history`, with permissions, a pay cooldown, a
  confirmation for `set`, and a confirmation for a payment above `economy.pay.confirmPercent` (default 50)
  percent of the payer's balance.
- Add `/skygrid eco audit` to verify that postings sum to zero and every balance equals the sum of
  its postings, and that counts the transactions per actor.
- Record an actor on every ledger transaction (player, admin, `console`, `plugin:<name>`,
  `plugin:unknown` or `system`), so console and plugin grants are identifiable.
- Add `Reward.CurrencyGrant` so a challenge task can reward coins. The credit is keyed by an
  idempotent `RewardKey` and is re-applied on join when a crash lost it.
- Add a Vault economy provider for the legacy `net.milkbowl.vault.economy.Economy` and for the
  VaultUnlocked `net.milkbowl.vault2.economy.Economy` service. Vault is a soft dependency; without it
  the plugin starts normally.
- Scope every balance and transaction by season, and close each season with one audited
  `SEASON_RESET` transaction per account that zeroes its balance.
- Add the `economy` section to the configuration, with currency names, starting balance, maximum
  balance, pay settings, the root-label toggle and Vault settings.
- State in the README that coins are never sold for real money, and quote this sentence: "Granting coins, shop items or prestige for real money (store plugins, `eco give`, Vault deposits) violates the Minecraft EULA and is unsupported."
- Out of scope (later changes): mailbox, admin shop, player market.

No **BREAKING** changes. The `RewardGrantor` port gains a `RewardKey` parameter, which is internal.

## Capabilities

### New Capabilities

- `player-wallet`: the coin balance of each player per season, the audited ledger that records every
  change of it, payments between players, admin adjustments, currency task rewards, the Vault provider
  and the ledger integrity check.

### Modified Capabilities

None.

## Impact

- Modules: `api` (money and ledger model, `Reward.CurrencyGrant`), `ports` (wallet ports), `core`
  (wallet service, cooldown, currency reward reconciler), `adapters/economy` (new), `adapters/config`
  (`economy` section, `currency` reward type, generated schema), `adapters/command` (economy
  handlers), `platforms/common` (economy executor, Vault providers, in-flight guard, i18n),
  `platforms/paper` (`softDepend`, permissions), `verification` (ArchUnit rules), `README.md`.
- Dependencies: `compileOnly` Vault API and VaultUnlocked API in `platforms/common` only.
- Persistence: new Flyway location and history table for the economy adapter, covered by the
  per-adapter migration test.
- Prerequisites: `add-season-lifecycle` (season ids, the current season and `ProgressScope`) and the
  Flyway per-adapter fix (PR #9).
- Affected behavior: challenge tasks accept a `currency` reward type; completing such a task credits
  the completing player.
