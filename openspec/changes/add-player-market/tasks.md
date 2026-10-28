# Tasks

Tests come before the production code they cover in every group (red, then green). Run only the touched
modules' tests, for example `./gradlew :core:test :adapters:economy:test --max-workers=2`.

## 1. Listing domain and tax (delivery slice 24, part 1)

- [ ] 1.1 Write `Listing` and `ListingState` tests for the state transitions (PENDING to ACTIVE, ACTIVE to SOLD, CANCELLED or EXPIRED) and rejection of any other transition, and verify they fail
- [ ] 1.2 Implement `Listing`, `ListingState` and the market settings record in `api.model` and verify the 1.1 tests pass
- [ ] 1.3 Write tax tests: 5 percent of 40 is 2, of 1 is 0, of 100 is 5, computed with exact arithmetic and floor division, and verify they fail
- [ ] 1.4 Implement the tax calculation in `core` and verify the 1.3 tests pass

## 2. Market service with a fake repository (delivery slice 24, part 2)

- [ ] 2.1 Write `MarketService` unit tests with an in-memory `MarketRepository` fake for listing validation (price bounds, item size, the 10 listing cap, creative refusal), and verify they fail
- [ ] 2.2 Write unit tests for purchase branches (own listing refused, expired refused, already sold refused, insufficient funds refused, success with postings and mailbox row) and verify they fail
- [ ] 2.3 Write unit tests for cancel, expiry batch and season-end closure, and verify they fail
- [ ] 2.4 Implement `MarketUseCase`, `MarketRepository` and `MarketService` in `ports` and `core` and verify the 2.1 to 2.3 tests pass

## 3. Market persistence (delivery slice 24, part 3)

- [ ] 3.1 Write Testcontainers tests that `V3__create_market.sql` creates `market_listing` after `V2` and that the indexes exist, and verify they fail
- [ ] 3.2 Add `db/migration/economy/V3__create_market.sql` with the table, the two indexes and the `CHECK` constraints, and verify the 3.1 tests pass
- [ ] 3.3 Write adapter tests for the listing insert with the per-seller advisory lock: the listing is inserted below the cap and refused at the cap, and verify they fail
- [ ] 3.4 Implement the Postgres `createPending`, `activate`, `discardPending` and the capped insert and verify the 3.3 tests pass
- [ ] 3.5 Write the race test: two concurrent sell commands at nine active listings create at most one listing, and verify it fails
- [ ] 3.6 Make the 3.5 test pass with the advisory lock and verify it is green

## 4. Purchase, cancel and expiry races (delivery slice 24, part 4)

- [ ] 4.1 Write the Testcontainers double-buy race test: two buyers from two threads on one listing give exactly one purchase, the loser's balance is unchanged and exactly one mailbox row exists, and verify it fails
- [ ] 4.2 Implement the purchase transaction (row lock, state and expiry check, ledger postings, sold state, mailbox row) and verify the 4.1 test passes
- [ ] 4.3 Write the cancel-versus-buy race test with a barrier: one winner and one mailbox row, and verify it fails
- [ ] 4.4 Implement the cancel transaction and verify the 4.3 test passes
- [ ] 4.5 Write the expiry-versus-buy test with the injected clock set to the expiry instant: one winner and one mailbox row, and verify it fails
- [ ] 4.6 Implement `expireDue` with `FOR UPDATE SKIP LOCKED` and batch 100, and verify the 4.5 test passes
- [ ] 4.7 Write a test that two concurrent `expireDue` calls never expire the same listing twice, and verify it fails; then verify it passes with 4.6
- [ ] 4.8 Write a test that a purchase by the seller is refused and that the purchase tax reaches `system:market_tax` with the sum of balances dropping by the tax, and verify they fail
- [ ] 4.9 Make 4.8 pass with the seller check and the tax posting and verify the `audit` stays clean afterwards

## 5. Season end closure (delivery slice 24, part 5)

- [ ] 5.1 Write a test that the season transition closes active and pending listings of the ending season into the mailbox with source `market_season_end`, and that closed listings leave the market, and verify it fails
- [ ] 5.2 Implement the season-end closure hook with the reconciliation of pending escrow first and verify the 5.1 test passes
- [ ] 5.3 Write a test that a listing whose expiry falls after the season end closes at the season end with its item lost, and that a listing expiring before the season end returns its item to the seller's mailbox, and verify it fails
- [ ] 5.4 Implement the ordering of expiry against the season end in the closure and verify the 5.3 test passes

## 6. Market GUI and commands (delivery slice 24, part 6)

- [ ] 6.1 Write handler tests for `/market [page]`, `/market sell <price>` and `/market search <item>` with `RecordingCommandSource`, and verify they fail
- [ ] 6.2 Implement `MarketCommands` for those subcommands and verify the 6.1 tests pass
- [ ] 6.3 Write MockBukkit tests that the listing confirm view shows price, tax and net, that creative sells are refused, that a full inventory keeps a purchase in the mailbox, and that the GUI shows no countdown text, and verify they fail
- [ ] 6.4 Implement `MarketView`, the listing confirm view, the expiry task and the permission `skygridng.economy.market` and verify the 6.3 tests pass
- [ ] 6.5 Write MockBukkit tests that the listing confirm view states the absolute season end and that the item is not returned when the season ends first, shows only the absolute expiry date otherwise, and that a seller's own listing view shows each absolute expiry date, and verify they fail
- [ ] 6.6 Implement those lines in `MarketView` and the listing confirm view and verify the 6.5 tests pass
- [ ] 6.7 Document the market, the 5 percent tax and the 48 hour listings in the README and verify the README states that coins are never sold for real money

## 7. Pull request

- [ ] 7.1 Open the pull request titled `feat(market): add player marketplace with item escrow`, branched from the remote main branch after `add-admin-shop` is merged, and verify the PR title is a valid Conventional Commit
