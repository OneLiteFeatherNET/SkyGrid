# Design

## Context

`add-admin-shop` provides the mailbox and the inventory journal; every item that leaves a seller's inventory into
escrow, or returns from escrow, uses that journal. `add-player-wallet` provides the ledger that the purchase
posts into. See proposal.md for motivation.

The market's core correctness problem is that a listing has four exits (buy, cancel, expire, season end) and
they can race. Exactly one must win, and the item must reach exactly one mailbox row.

## Goals / Non-Goals

**Goals:**
- Every listing leaves the market exactly once, with its item delivered exactly once and its money moved exactly once.
- Escrow that survives a crash, through the same journal as the shop.
- A fixed, visible tax that removes coins from circulation.

**Non-Goals:**
- Price history, auctions, bids, price-sorted browsing (the Could item J1j is deferred).
- Editing a listing's price; a seller cancels and lists again.
- Cross-server markets (one server per database in v1).

## Decisions

**Table `market_listing` (migration `V3`).** Columns as in `economy.md` section 2.4, with `state` in
`PENDING|ACTIVE|SOLD|CANCELLED|EXPIRED`, `buyer_id`, `closed_at` and `version BIGINT NOT NULL DEFAULT 0` (`@Version`).
Indexes on `(season_id, state, expires_at)` and `(season_id, seller_id, state)`. Alternative: keep listings in the
shop tables (rejected: different lifecycle).

**Listing aggregate and state (GoF State).** `ListingState` is a sealed interface permitting `Pending`, `Active`,
`Sold`, `Cancelled` and `Expired`. The `Listing` aggregate, an immutable record in `api.model` and `core`, exposes
`activate(at)`, `purchase(buyer, now)`, `cancel(now)`, `expire(now)` and `closeForSeasonEnd()`. Each returns a new
`Listing` or a `ListingRejection` (sealed: `NotActive`, `Expired`, `OwnListing`), so an illegal transition, a purchase by the seller and a purchase after expiry are
domain outcomes, not SQL `WHERE` clauses. The `ListingEntity` JPA class lives only in `adapters/economy`, with a Data
Mapper between entity and record. The row lock (`PESSIMISTIC_WRITE`) and `@Version` only serialize concurrent
writers; they do not hold the business rules.

**Listing flow.** Step 1 (async): insert the listing as `PENDING`, which buyers never see. The insert runs in a
transaction that first loads the seller's economy account row with `PESSIMISTIC_WRITE` (every lister has an account),
counts the seller's `PENDING` and `ACTIVE` listings with a named query and persists the listing only if the count is
below the cap. The account row lock is the addition to the design draft that makes the cap hold under two concurrent
sell commands. Step 2 (main thread): verify the held stack still equals the snapshot, remove it, add the listing id
to the journal, save the player. Step 3 (async): `listing.activate(at)` sets `ACTIVE` and
`expires_at = created + duration`, then the main thread drops the journal id. Reconciliation of an interrupted
listing follows the item-mailbox table.

**Purchase.** One Unit of Work (`inTransaction`): load the listing with `PESSIMISTIC_WRITE` and call
`listing.purchase(buyer, now)`, which rejects a listing that is not `Active`, one whose `expires_at` has passed, and a
purchase by its seller. On success post the `MarketSale` ledger operation (buyer `-price`, seller `+(price - tax)`,
`system:market_tax` `+tax`) under the purchase's idempotency key, and persist the listing as `Sold` with `buyer_id`
and the `MARKET_SOLD` delivery. Tax is `price * taxBasisPoints / 10000` with floor division, computed with
`multiplyExact`. A second buyer blocks on the row lock and then sees `Sold`, so the aggregate rejects it as "already
sold" with no balance change. `@Version` is the second line of defence; a lost race there surfaces as
`OptimisticLockException`, mapped to the same outcome.

**Cancel and expiry.** Cancel loads the listing with `PESSIMISTIC_WRITE` and calls `listing.cancel(now)`, which
accepts only an `Active` listing whose `expires_at` has not passed, then persists it as `Cancelled` with the
`MARKET_CANCELLED` delivery. Expiry runs as an async repeating task every 60 s through `expireDue`, the one repository
method that uses `LockOptions.SKIP_LOCKED`: a `PESSIMISTIC_WRITE` named query with the
`jakarta.persistence.lock.timeout` hint set to `LockOptions.SKIP_LOCKED`, a maximum of 100 rows, and each row goes
through `listing.expire(now)`, which persists it as `Expired` with the `MARKET_EXPIRED` delivery. This Hibernate-specific
hint is the single encapsulated exception to the JSR 338 rule and stays inside that method. Because purchase and
cancel check the expiry in the aggregate under the row lock, an expiry and a purchase on the same listing resolve to
one winner.

**Season end.** Market close is a `SeasonTransitionListener` registered through the Guice `Multibinder` in
`platforms/common`, so the season core never names economy types. After the join-style reconciliation of pending
escrow, it loads every `Pending` and `Active` listing of the ending season and calls `closeForSeasonEnd()` on each,
which persists it as `Cancelled` with a `MARKET_SEASON_END` delivery. No new state is introduced. The deliveries then
follow the season-end policy of `add-admin-shop`.

**Defaults.** The economy draft fixes the tax (500 basis points), duration (48 h), maximum listings (10) and maximum item
bytes (32768). It does not fix the price bounds. This change uses `minPrice = 1` and `maxPrice = maxBalance` as defaults,
both configurable. Pending listings count toward the 10-listing cap so that an in-flight listing cannot exceed it.

**Browsing.** The GUI reads one page of active listings ordered by `created DESC`, with an optional item id filter.
Search is a named query with bound parameters only.

**Threading.** Same executor and in-flight guard as the wallet. The GUI is rebuilt from a page read, never from cached
listings, so a sold listing cannot be shown as available after its purchase commits.

## Risks / Trade-offs

- [Double purchase] → row lock and the state check in the same transaction; race test with two buyers.
- [Cancel versus buy] and [expiry versus buy] → both check the aggregate's `Active` state and expiry after the
  `PESSIMISTIC_WRITE` load; race tests assert one winner and one mailbox row.
- [Listing cap exceeded by concurrent commands] → `PESSIMISTIC_WRITE` on the seller's account row inside the insert
  transaction; race test.
- [Item dupe across a crash during listing] → escrow journal, reconciliation, verification against the snapshot.
- [Oversized item payloads (book or shulker NBT bombs)] → `maxItemBytes` checked before escrow; payload bytes are never
  deserialized off the main thread.
- [Market spam] → listing cap, price bounds, one-step listing confirm.
- [Self-trading to move coins] → buying an own listing is refused; the tax still makes a round trip lossy.
- [Season end wipes listed items] → the design decision is that listings close into the ending season's mailbox and then
  follow the season policy, so they are lost with the season. This follows the approved decision in `economy.md`
  section 6.1 and is flagged for product review; it is not changed here.
- [Expiry task delayed] → a 60 s cadence; expired listings cannot be bought after their expiry because the `Listing`
  aggregate checks the expiry itself.

## I5 dark-pattern review (challenge-season-system, items 1 to 8)

1. **Drives.** Ownership and social influence (trading with other players). No randomness, no gambling, no loss
   avoidance in the 48 hour cycle: expiry returns the item to the seller's mailbox. Season end is different and is
   stated as a loss: a listing still open when the season ends closes, and its item is not returned. It is queued
   in the ending season's mailbox, which expires with the season (`item-mailbox`). The listing confirm view states
   the absolute season end and that the item is not returned, and each seller sees the absolute expiry of every
   own listing (spec: Creating a listing). Verdict: **pass, conditional on that preview**.
2. **Transparency.** Price, tax and the seller's net are shown before a listing is confirmed, together with the
   absolute expiry date and, when the season ends first, the season end and the non-return of the item; the buy
   confirm shows the price and the balance after it; the tax rate is shown in the `/market` header.
3. **Bartle.** Socializers and achievers trade; explorers browse. Killers are not served. No leaderboard.
4. **Adaptive rewards.** None; the tax and bounds are static configuration.
5. **Late joiners.** A late joiner can buy at fixed prices and cannot be shut out of trading. Season close resets the
   market with the season; a late joiner who had listings in the old season has the same loss as everyone else, and
   the expiry date is shown on each listing and in the season announcements.
6. **Outside observer.** The market trades in-game items for in-game coins; the README states that coins are never sold for
   money; no real-money path exists.
7. **Metrics.** Sell-through rate of listings (sold over closed); share of active players with at least one trade per week;
   the median listing price against the shop price (for inflation); count of listings closed as `market_expire` against
   `market_cancel` (a high expire ratio signals overpricing); count of listings closed as `market_season_end` (items lost
   at season end, should be compared against the expire ratio).
8. **Timers.** The 48 h expiry is organizational: no countdown or "only N left" text is shown. The two endings differ and
   both are shown before a listing is confirmed: at 48 hours the item returns, at season end it is lost. Both are
   stated with absolute dates, not as a countdown. Verdict: **pass**.

## Abuse and security review

| Threat | Mitigation |
|---|---|
| Double purchase | row lock, state check, single winner test |
| Item dupe between escrow and inventory | journal, verification against snapshot, reconciliation |
| Listing cap bypass by concurrent commands | `PESSIMISTIC_WRITE` on the seller's account row inside the insert transaction |
| Buying own listing to move coins | refused |
| Oversized item payloads | `maxItemBytes` before escrow |
| Price overflow | whole-coin `Money`, `multiplyExact` for tax, DB `CHECK (price > 0)` |
| SQL injection through search | named query with bound parameters only; no `createNativeQuery` |

## Test plan (F.I.R.S.T.)

- Unit (core, in-memory fakes): tax math including rounding to zero; listing validation branches; purchase branches
  (own listing, expired, sold, insufficient funds); season close; every `ListingState` against every action, table-driven.
- Testcontainers (adapters/economy):
  - two concurrent buyers on one listing from two threads: exactly one `Purchased`, the loser's balance unchanged, one mailbox row;
  - cancel versus buy with a barrier: one winner and exactly one mailbox row;
  - expire versus buy with a fixed test clock at the expiry instant: one winner and exactly one mailbox row;
  - two concurrent listings at nine active listings: at most one created;
  - `expireDue` with `SKIP_LOCKED` across two transactions: no listing expired twice;
  - a purchase with a stale `@Version`: `OptimisticLockException` mapped to "already sold", balance unchanged.
- MockBukkit (platforms/common): journal reconciliation for interrupted listings; creative refusal; full inventory on purchase
  keeps the stack in the mailbox; GUI confirm shows price, tax and net; search filters by item id.
- No `Thread.sleep`; expiry uses the injected test clock; every concurrency test asserts results, not timing.

## Migration Plan

- Deploy after `add-admin-shop`. `V3` creates one table and changes no existing rows.
- Rollback: redeploy the previous build. Active listings remain in the table; the older build does not expire them, so
  the operator must cancel them with the admin runbook before rolling back. Items already in escrow are never deleted.
