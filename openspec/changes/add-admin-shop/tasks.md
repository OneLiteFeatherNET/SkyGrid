# Tasks

Tests come before the production code they cover in every group (red, then green). Run only the touched
modules' tests, for example `./gradlew :core:test :adapters:economy:test --max-workers=2`.

## 1. Mailbox domain and ports (delivery slice 22, part 1)

- [ ] 1.1 Write `ItemPayload` tests for defensive copies in and out and for equality by bytes, and verify they fail
- [ ] 1.2 Implement `ItemPayload` and `Delivery` in `api.model` and verify the 1.1 tests pass
- [ ] 1.3 Write `MailboxService` unit tests with an in-memory `MailboxRepository` fake for queueing, the whole-stack rule and claiming a subset, and verify they fail
- [ ] 1.4 Implement `MailboxUseCase`, `MailboxRepository` and `MailboxService` in `ports` and `core` and verify the 1.3 tests pass
- [ ] 1.5 Write table-driven tests for `IntentState` (every state against `commit` and `abort`: only `Pending` accepts either, `Committed` and `Aborted` reject both) and for `Delivery` (`markDelivered` succeeds once, a second call is rejected), and verify they fail
- [ ] 1.6 Implement the sealed `DeliverySource` (permits `ShopPurchase`), the sealed `IntentState` (`Pending`, `Committed`, `Aborted`) and the `SaleIntent` aggregate in `api.model` and `core`, and verify the 1.5 tests pass

## 2. Mailbox persistence (delivery slice 22, part 2)

- [ ] 2.1 Write Testcontainers tests that `V2__create_mailbox_and_shop.sql` creates `mailbox_delivery` and `shop_sale_intent` after `V1` and that the migration history stays in `flyway_history_economy`, and verify they fail
- [ ] 2.2 Add `db/migration/economy/V2__create_mailbox_and_shop.sql` with the two tables, a `version BIGINT NOT NULL DEFAULT 0` column on both, the pending partial index and the `CHECK` constraints, and verify the 2.1 tests pass
- [ ] 2.3 Write adapter tests for the buy Unit of Work (`ShopPurchase` posting and `Delivery` row committed together, both rolled back when the delivery insert fails), and verify they fail
- [ ] 2.4 Implement the JPA `MailboxRepository` (`DeliveryEntity` with a mapper to the `Delivery` record) including the buy Unit of Work, `pendingFor`, `markDelivered` and the season scoping, and verify the 2.3 tests pass
- [ ] 2.5 Write a Testcontainers race test: two concurrent buys by one player whose total exceeds the balance produce exactly one success and a balance that never goes negative, and verify it fails
- [ ] 2.6 Make the 2.5 test pass with `PESSIMISTIC_WRITE` on the buyer account row and verify it is green
- [ ] 2.7 Write a test that a pending row of an earlier season is not returned for the active season, and verify it fails; then implement the season filter and verify it passes
- [ ] 2.8 Write an ArchUnit rule test in `verification` that `jakarta.persistence..` and `org.hibernate..` are used only in `..adapters..`, that `@Entity` classes live only in `..adapters..`, and that no code calls `EntityManager#createNativeQuery`, and verify it fails on a fixture class outside `adapters`
- [ ] 2.9 Write adapter tests that `DeliveryEntity` and `SaleIntentEntity` carry `@Version`, that the shared `MoneyConverter` and `SeasonIdConverter` round-trip, and that Hibernate `validate` accepts the V2 schema, and verify they fail
- [ ] 2.10 Implement the entities, the entity-to-record mappers, the shared converters in `adapters/persistence` and the `@NamedQuery` declarations, and verify the 2.9 tests and the 2.8 rule pass
- [ ] 2.11 Write the Testcontainers race test: two concurrent `markDelivered` calls on one delivery give exactly one success, and the loser's `OptimisticLockException` maps to "already delivered", and verify it fails
- [ ] 2.12 Implement the mapping of `OptimisticLockException` to the delivery outcome and verify the 2.11 test passes

## 3. Inventory journal and reconciliation (delivery slice 22, part 3)

- [ ] 3.1 Write table-driven tests for the reconciliation rows (pending listing or sale intent in the journal finishes, not in the journal is discarded, delivery in the journal is marked delivered, delivery not in the journal is delivered again, stale journal id is dropped), with a fake repository, and verify they fail
- [ ] 3.2 Implement the `JournalReconciler` in `platforms/common` against the port and verify the 3.1 tests pass
- [ ] 3.3 Write MockBukkit tests that the journal entry and the inventory change happen in the same tick and that `saveData` is called after them, and verify they fail
- [ ] 3.4 Implement the PDC journal with a 64 entry bound and the same-tick save and verify the 3.3 tests pass
- [ ] 3.5 Write MockBukkit tests that a stack changed after the snapshot is discarded with no other change, and verify they fail
- [ ] 3.6 Implement the snapshot verification and verify the 3.5 test passes
- [ ] 3.7 Write MockBukkit tests for the whole-stack rule (full inventory keeps the stack pending and tells the player to claim) and for `/market claim` moving only the fitting stacks, and verify they fail
- [ ] 3.8 Implement the immediate delivery, the join reconciliation hook and the `claim` subcommand registration under `/market`, and verify the 3.7 tests pass
- [ ] 3.9 Write MockBukkit tests that a join with 2 pending items sends the pending count and the absolute season end date, and verify they fail
- [ ] 3.10 Implement the count and end-date message in the join reconciliation hook and verify the 3.9 test passes
- [ ] 3.11 Write a Guice test that the `Multibinder` set of `SeasonTransitionListener` in `platforms/common` contains the mailbox season policy and that the season core calls each registered listener once, and verify it fails
- [ ] 3.12 Implement `MailboxSeasonPolicy` as a `SeasonTransitionListener` (pending deliveries of the ending season expire, none are carried over) and its Multibinder registration in `platforms/common`, and verify the 3.11 test passes
- [ ] 3.13 Run `./gradlew :platforms:common:test :adapters:economy:test --max-workers=2` and verify green

## 4. Shop configuration (delivery slice 23, part 1)

- [ ] 4.1 Write config round-trip tests under `@TempDir` for a shop file in JSON, YAML and TOML, and verify they fail
- [ ] 4.2 Implement `ShopDtos`, `JacksonShopRepository`, the bundled `shops/general.yml` and the `ShopCatalog` and `ShopEntry` records, and verify the 4.1 tests pass
- [ ] 4.3 Write tests that the generated `schema/shop.schema.json` lists the entry keys and that an entry with no price is rejected with an error naming it, and verify they fail
- [ ] 4.4 Implement the schema generation in `ConfigSchemas` and the entry validation, and verify the 4.3 tests pass
- [ ] 4.5 Write `ShopService` tests for the arbitrage rule: a sell price above a buy price elsewhere rejects the file and names both entries, equal unit prices pass, and a sell-only entry passes, and verify they fail
- [ ] 4.6 Implement the exact cross-multiplied arbitrage check and verify the 4.5 tests pass
- [ ] 4.7 Write a test that an unknown item id disables the entry with a warning and shows no placeholder, and verify it fails
- [ ] 4.8 Implement the item resolution in `platforms/common` with the disable-and-warn path and verify the 4.7 test passes

## 5. Shop buy and sell (delivery slice 23, part 2)

- [ ] 5.1 Write `ShopService` unit tests for buy (debit and queued delivery, insufficient funds refused) and sell (exact quantity removed, too few items refused, credit once), and verify they fail
- [ ] 5.2 Implement `ShopUseCase` and `ShopService` buy and sell in `ports` and `core` and verify the 5.1 tests pass
- [ ] 5.3 Write tests that the credit key `shopsale:<intentId>` is applied at most once (a duplicate key raises `ConstraintViolationException`, mapped to `AlreadyApplied`) and that committing an intent that is already committed is refused, and verify they fail
- [ ] 5.4 Implement the sell intent flow as one Unit of Work in `MailboxRepository` (`ShopSale` posting and `SaleIntent` commit) and the sell pipeline in `platforms/common`, and verify the 5.3 tests pass
- [ ] 5.5 Write MockBukkit tests that only pristine stacks match a sale (renamed, enchanted or damaged stacks are not counted) and that creative and spectator sells are refused, and verify they fail
- [ ] 5.6 Implement the pristine match and the mode check and verify the 5.5 tests pass
- [ ] 5.7 Write a crash-recovery test: an intent whose id is in the journal at join is completed exactly once, and verify it fails; then implement and verify it passes
- [ ] 5.8 Write the Testcontainers race test: two concurrent commits of one sale intent give exactly one credit, and the loser's `OptimisticLockException` maps to the already-committed outcome, and verify it fails
- [ ] 5.9 Implement the `OptimisticLockException` mapping for sale intents and verify the 5.8 test passes

## 6. Shop GUI and command (delivery slice 23, part 3)

- [ ] 6.1 Write MockBukkit tests that shift-click, hotbar swap, drag and double-click collect in a shop view move no item and change no balance, and that display items carry the PDC tag, and verify they fail
- [ ] 6.2 Implement `ShopView`, `ConfirmView`, the cancel-first handlers and the display copies and verify the 6.1 tests pass
- [ ] 6.3 Write handler tests for `/shop [id]` (open a shop, unknown shop id reports and opens nothing) with `RecordingCommandSource`, and verify they fail
- [ ] 6.4 Implement `ShopCommands` and the `shop` root label behind `economy.commands.rootLabels`, and verify the 6.3 tests pass
- [ ] 6.5 Write a test that a player without `skygridng.economy.shop` is refused, and verify it fails; then declare the permission in `plugin.yml` and verify it passes
- [ ] 6.6 Document `/shop`, the shop file layout and the arbitrage rule in the README and verify the README names the shops directory
- [ ] 6.7 Write MockBukkit tests that the buy confirm view in the final 24 hours of a season states the absolute season end and that unclaimed items are lost, and that it states nothing about the season end earlier, and verify they fail
- [ ] 6.8 Implement the season end line in `ConfirmView` and verify the 6.7 tests pass

## 7. Pull request

- [ ] 7.1 Open the pull request titled `feat(shop): add configurable admin shop with crash-safe mailbox`, branched from the remote main branch after `add-player-wallet` is merged, and verify the PR title is a valid Conventional Commit
