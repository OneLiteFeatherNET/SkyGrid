# Design

## Context

`add-player-wallet` provides the ledger, the season-scoped accounts and the `system:shop` account. This change
adds the two stores that the ledger does not cover: the Postgres mailbox and sale intents, and the player's
inventory, which is durable only when the player data file is saved. See proposal.md for motivation.

The inventory is the hard part. Postgres commits are durable immediately; the inventory is durable only at
the next player data save, so a crash can roll it back to an older state while the database already moved on.
The journal described below makes the two stores agree after any crash.

## Goals / Non-Goals

**Goals:**
- No item is duplicated or lost by a crash between a database commit and an inventory change.
- A purchase never leaves the buyer charged without a pending item, and never delivers an item without a charge.
- Shops are plain configuration that the admin can reason about, with no price arbitrage between them.

**Non-Goals:**
- Player-to-player trading (market change) and the listing escrow.
- Dynamic prices, stock limits or restocking. Prices are fixed in configuration.
- Items in a shop that a player can customize (only pristine items are sold).

## Decisions

**Mailbox and sale intents in the economy adapter, migration `V2`.** The mailbox row must commit in the same
transaction as the ledger posting, which needs one persistence unit with the ledger. The `economy` persistence
unit covers the ledger, the mailbox and the sale intents, so one Unit of Work can post and deliver. Tables:
`mailbox_delivery` (id, season, player, item id, amount, item data as BYTEA, source discriminator `source`,
`source_ref`, delivered at, created at, `version BIGINT NOT NULL DEFAULT 0`) with a partial index on pending rows,
and `shop_sale_intent` (id, season, player, shop, item, amount, payout, state `PENDING|COMMITTED|ABORTED`,
`version BIGINT NOT NULL DEFAULT 0`). `ItemPayload` keeps the platform bytes opaque; the item id and amount exist
only for display and search. Alternative: a file-based mailbox (rejected: no atomicity with the ledger).

**Domain model and mapping (Data Mapper, GoF State).** `Delivery` and `SaleIntent` are aggregates: immutable records
in `api.model` and `core`. `DeliverySource` is a sealed interface that permits `ShopPurchase` (a record carrying the
purchase reference); the market change adds its sources to the same permits list. `IntentState` is a sealed
interface permitting `Pending`, `Committed` and `Aborted`; `commit()` and `abort()` return a new state, and an
illegal transition is a domain rejection. The JPA `@Entity` classes (`DeliveryEntity`, `SaleIntentEntity`) live
only in `adapters/economy`, with a Data Mapper between entity and record. `DeliverySource` is stored as the
discriminator text in `source` plus the reference in `source_ref`; `Money` and `SeasonId` map through the shared
converters in `adapters/persistence`. Both aggregates carry `@Version long version`; a lost race surfaces as
`OptimisticLockException`, which the adapter maps to the domain outcome.

**Buy is one transaction.** One Unit of Work (`inTransaction`) posts the `ShopPurchase` ledger operation (buyer
debit, shop credit) and persists the `Delivery` aggregate in the same transaction. The buyer's account row is
loaded with `PESSIMISTIC_WRITE`, and the ledger's lock order rules from `add-player-wallet` apply unchanged. Two
concurrent buys by one player serialize on the buyer's account row.

**Inventory journal in the player data file (PDC).** A PDC list `skygridng:economy_journal` of operation UUIDs on
the `Player`, bounded at 64 entries. The inventory change and the journal write happen in the same main-thread
tick, followed by `Player#saveData()`. Any saved state therefore contains both or neither. Alternative: a
journal table in Postgres only (rejected: it cannot tell whether the inventory change was saved).

**Movement steps.** Sell follows: persist a `SaleIntent` in `Pending` (async), verify and remove the items and add
the intent id to the journal then save (main thread), then in one Unit of Work post the `ShopSale` ledger operation
with key `shopsale:<intent>` and commit the aggregate with `intent.commit()` (async). The key is a `@NaturalId`
with a unique constraint, so a duplicate posting raises `ConstraintViolationException`, which the adapter maps to
`AlreadyApplied`. Delivery follows: the `Delivery` is committed with the money; on the main thread add the item and
the delivery id to the journal and save; then `delivery.markDelivered(now)` through the aggregate and drop the
journal id. A concurrent second delivery attempt loses on `@Version`, and its `OptimisticLockException` maps to
"already delivered". Reconciliation on join runs the table of the item-mailbox spec, before any new delivery.

**Arbitrage check.** `ShopService` compares unit prices with exact integer cross-multiplication
(`sell * buyQuantity > buy * sellQuantity`) across all loaded shops, including the entry itself. A violating file
is rejected at load with both entries named, so no shop can be configured into a money loop.

**Shop configuration.** `shops/<id>.<ext>` is parsed by `ShopDtos` through the same Jackson setup as the other config
folders, and the schema `schema/shop.schema.json` is generated by `ConfigSchemas`. Item ids are namespaced
`ItemId`s and are resolved to a platform material only in `platforms/common`; an unknown id disables that entry
with a warning, so no placeholder barrier block can be sold.

**GUI.** Plain chest inventories with a custom `InventoryHolder` per view (`ShopView`, `MailboxView`,
`ConfirmView`). Every click and drag touching a holder is cancelled first and interpreted by slot. Display items
are copies tagged with PDC key `skygridng:gui_display`. Opening a view from a Bukkit-free handler goes through the
`EconomyViews` out port implemented in `platforms/common`.

**Commands.** `/shop [id]` and the `claim` subcommand of `/market`. The `/market` root label is registered by this
change with only `claim`; `add-player-market` adds the other subcommands to the same root. `/shop` is a root label
behind `economy.commands.rootLabels`, as decided in `economy.md` section 6.2.

**Season end.** Pending deliveries expire with the season, under the inventory policy of `add-season-lifecycle`:
they are never carried into the next season, the same as the inventory they would have entered. The mailbox season
policy is a `SeasonTransitionListener` registered through a Guice `Multibinder` in `platforms/common`; the season
core iterates the registered listeners and never names economy types.

## Risks / Trade-offs

- [Crash between item removal and save] → the journal entry is written in the same tick and saved immediately;
  reconciliation completes or discards the intent on join. Covered by table-driven tests of every journal row.
- [Crash while the player is offline] → the save happens on logout as on every server; the reconciliation table
  handles both outcomes.
- [Item duplication through GUI clicks (shift-click, number keys, double-click collect, drag, cursor on close)] →
  every touching event is cancelled first; display items are copies; the in-flight guard drops repeated clicks.
- [Selling a damaged, renamed or enchanted item at full price] → pristine match only.
- [Selling creative-spawned items] → creative and spectator sells refused.
- [Shop arbitrage] → load-time rejection with both entries named.
- [Full inventory on purchase] → whole-stack rule; the item waits in the mailbox and the player runs `/market claim`.
- [Main-thread stall from `saveData`] → one save per movement on the main thread, and saves are batched per tick when
  several movements occur together.
- [Item data bomb in mailbox payloads] → payloads are opaque bytes, never deserialized off the main thread, and bounded by the
  market's `maxItemBytes` for listings; shop stock comes from admin files only.
- [SQL injection] → JPA named queries with bound parameters only; no `createNativeQuery`.

## I5 dark-pattern review (challenge-season-system, items 1 to 8)

1. **Drives.** Ownership (items in a shop) and accomplishment (buying what the grid lacks). Prices are fixed; there is no
   random stock, no crate and no timed restock.
2. **Transparency.** Every price, quantity and the balance are shown in the GUI before a click; the buy confirm names
   the balance after the purchase and, in the final 24 hours of a season, the absolute season end and that unclaimed
   items are lost at that time (spec: Buy confirm states the season end).
3. **Bartle.** Explorers use the catalogue as a guide to what the grid holds; achievers and socializers are not
   affected by the shop.
4. **Adaptive rewards.** None; shop prices are static configuration.
5. **Late joiners.** Shop prices do not depend on how early a player joined. Mailbox items that are not claimed by the
   season end expire with the season. That is a loss, so it is communicated three ways: on join (pending count and
   absolute end date), in every season announcement (the player's own pending count), and on the buy confirm view in
   the final 24 hours. Verdict: **pass, conditional on those three communications**.
6. **Outside observer.** Shops trade only in-game items for in-game coins; no real-money path.
7. **Metrics.** Share of players who bought or sold at least once per week; count of pending deliveries older than one
   day (should trend to zero); count of journal reconciliations that discarded an intent (should stay near zero); count
   of pending deliveries expired at season end, per season.
8. **Timers.** No timer or countdown in the shop. Pending deliveries wait without expiring during a season; the season end
   is shown as an absolute date in the places listed in item 5, not as a countdown.

## Abuse and security review

| Threat | Mitigation |
|---|---|
| Money lost or created when a purchase crashes | one transaction for debit and mailbox row |
| Two buys at once overdraw the balance | `PESSIMISTIC_WRITE` on the buyer account row; concurrency test |
| Item dupe through a crash between database and inventory | journal in the player data file, same-tick save, reconciliation |
| GUI dupes | cancel-first handlers, display copies, in-flight guard |
| Selling items not from the shop | pristine match, creative and spectator refusal |
| Arbitrage loop between shops | load-time rejection |
| Unknown item id turns into a sellable placeholder | entry disabled with a warning |
| Overflow in prices or payouts | whole-coin `Money`, exact arithmetic, DB `CHECK (payout > 0)` |

## Test plan (F.I.R.S.T.)

- Unit (core, in-memory fakes): arbitrage check across catalogs including equal prices and the same entry; buy and
  sell branches; intent state machine (every state against every action, table-driven).
- Testcontainers (adapters/economy): buy with mailbox row in one transaction, failure rollback, two concurrent buys that
  exceed the balance, sale intent commit and abort, `V2` migration after `V1`, two concurrent `markDelivered` calls and two concurrent
  intent commits (one `OptimisticLockException`, mapped to the domain outcome), a duplicate `shopsale` key (mapped to
  `AlreadyApplied`), and Hibernate `validate` against the V2 schema.
- MockBukkit (platforms/common): journal reconciliation rows one by one with a fake repository; GUI cancels shift-click,
  hotbar swap, drag and double-click collect; display items carry the PDC tag; full inventory keeps the stack in the
  mailbox; creative refusal; unknown item disables the entry.
- Config (`@TempDir`): shop round trip in JSON, YAML and TOML; schema generated; arbitrage rejection message names both
  entries.

## Migration Plan

- Deploy after `add-player-wallet`. `V2` creates two tables and adds no change to existing rows.
- Rollback: redeploy the previous build; pending deliveries remain in the table and are delivered again by the next
  build that has the mailbox. Items already added to inventories stay there.
