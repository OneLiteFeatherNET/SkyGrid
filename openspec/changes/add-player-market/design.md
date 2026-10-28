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
`PENDING|ACTIVE|SOLD|CANCELLED|EXPIRED`, `buyer_id` and `closed_at`. Indexes on `(season_id, state, expires_at)` and
`(season_id, seller_id, state)`. Alternative: keep listings in the shop tables (rejected: different lifecycle).

**Listing flow.** Step 1 (async): insert the listing as `PENDING`, which buyers never see. The insert runs in a
transaction that first takes `pg_advisory_xact_lock(hashtext(seller_id))`, counts the seller's `PENDING` and `ACTIVE`
listings and inserts only if the count is below the cap. The advisory lock is the addition to the design draft
that makes the cap hold under two concurrent sell commands. Step 2 (main thread): verify the held stack still equals
the snapshot, remove it, add the listing id to the journal, save the player. Step 3 (async): set `ACTIVE` and
`expires_at = created + duration`, then the main thread drops the journal id. Reconciliation of an interrupted
listing follows the item-mailbox table.

**Purchase.** One transaction: lock the listing row `FOR UPDATE`, require `state = 'ACTIVE' AND expires_at > now`
and `seller_id <> buyer`, post the ledger postings (buyer `-price`, seller `+(price - tax)`, `system:market_tax`
`+tax`) under the purchase's idempotency key, set `SOLD` with `buyer_id`, and insert the mailbox row. Tax is
`price * taxBasisPoints / 10000` with floor division, computed with `multiplyExact`. A second buyer blocks on the row
lock and then sees `SOLD`, so it gets "already sold" with no balance change.

**Cancel and expiry.** Cancel locks the row and requires `ACTIVE AND expires_at > now`, then sets `CANCELLED` and
inserts the mailbox row with source `market_cancel`. Expiry runs as an async repeating task every 60 s: `expireDue`
selects due `ACTIVE` rows with `FOR UPDATE SKIP LOCKED`, batch 100, sets `EXPIRED` and inserts mailbox rows with
source `market_expire`. Because purchase and cancel require `expires_at > now` under the row lock, an expiry and a
purchase on the same listing resolve to one winner.

**Season end.** The season transition closes every `ACTIVE` listing and every `PENDING` listing of the ending season,
after the join-style reconciliation of pending escrow, sets them `CANCELLED` and inserts mailbox rows with source
`market_season_end`. No new state is introduced. The rows then follow the season-end policy of `add-admin-shop`.

**Defaults.** The economy draft fixes the tax (500 basis points), duration (48 h), maximum listings (10) and maximum item
bytes (32768). It does not fix the price bounds. This change uses `minPrice = 1` and `maxPrice = maxBalance` as defaults,
both configurable. Pending listings count toward the 10-listing cap so that an in-flight listing cannot exceed it.

**Browsing.** The GUI reads one page of active listings ordered by `created DESC`, with an optional item id filter.
Search uses the item id column and parameter binding only.

**Threading.** Same executor and in-flight guard as the wallet. The GUI is rebuilt from a page read, never from cached
listings, so a sold listing cannot be shown as available after its purchase commits.

## Risks / Trade-offs

- [Double purchase] → row lock and the state check in the same transaction; race test with two buyers.
- [Cancel versus buy] and [expiry versus buy] → both require `ACTIVE AND expires_at > now` under the row lock; race tests
  assert one winner and one mailbox row.
- [Listing cap exceeded by concurrent commands] → per-seller advisory lock inside the insert transaction; race test.
- [Item dupe across a crash during listing] → escrow journal, reconciliation, verification against the snapshot.
- [Oversized item payloads (book or shulker NBT bombs)] → `maxItemBytes` checked before escrow; payload bytes are never
  deserialized off the main thread.
- [Market spam] → listing cap, price bounds, one-step listing confirm.
- [Self-trading to move coins] → buying an own listing is refused; the tax still makes a round trip lossy.
- [Season end wipes listed items] → the design decision is that listings close into the ending season's mailbox and then
  follow the season policy, so they are lost with the season. This follows the approved decision in `economy.md`
  section 6.1 and is flagged for product review; it is not changed here.
- [Expiry task delayed] → a 60 s cadence; expired listings cannot be bought after their expiry because the purchase
  checks `expires_at > now` itself.

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
| Listing cap bypass by concurrent commands | advisory lock inside the insert transaction |
| Buying own listing to move coins | refused |
| Oversized item payloads | `maxItemBytes` before escrow |
| Price overflow | whole-coin `Money`, `multiplyExact` for tax, DB `CHECK (price > 0)` |
| SQL injection through search | parameter binding only |

## Test plan (F.I.R.S.T.)

- Unit (core, in-memory fakes): tax math including rounding to zero; listing validation branches; purchase branches
  (own listing, expired, sold, insufficient funds); season close.
- Testcontainers (adapters/economy):
  - two concurrent buyers on one listing from two threads: exactly one `Purchased`, the loser's balance unchanged, one mailbox row;
  - cancel versus buy with a barrier: one winner and exactly one mailbox row;
  - expire versus buy with a fixed test clock at the expiry instant: one winner and exactly one mailbox row;
  - two concurrent listings at nine active listings: at most one created;
  - `expireDue` with `SKIP LOCKED` across two transactions: no listing expired twice.
- MockBukkit (platforms/common): journal reconciliation for interrupted listings; creative refusal; full inventory on purchase
  keeps the stack in the mailbox; GUI confirm shows price, tax and net; search filters by item id.
- No `Thread.sleep`; expiry uses the injected test clock; every concurrency test asserts results, not timing.

## Migration Plan

- Deploy after `add-admin-shop`. `V3` creates one table and changes no existing rows.
- Rollback: redeploy the previous build. Active listings remain in the table; the older build does not expire them, so
  the operator must cancel them with the admin runbook before rolling back. Items already in escrow are never deleted.
