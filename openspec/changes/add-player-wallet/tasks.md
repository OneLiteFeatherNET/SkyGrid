# Tasks

Tests come before the production code they cover in every group (red, then green). Run only the touched
modules' tests, for example `./gradlew :api:test :core:test --max-workers=2`.

## 1. Money model and wallet service (delivery slice 17)

- [ ] 1.1 Write `Money` tests for addition and subtraction overflow, negative results and whole-coin values, and verify they fail
- [ ] 1.2 Implement `Money` in `api.model` with exact arithmetic and verify the 1.1 tests pass
- [ ] 1.3 Write `EconomySettings.parse` tests for "42" (accepted), "1.5", "1e3", "0", "-1" and blank (rejected without rounding), and verify they fail
- [ ] 1.4 Implement `EconomySettings.parse` with `BigDecimal.longValueExact` and verify the 1.3 tests pass
- [ ] 1.5 Write `Posting` and `TransferResult` tests for sum-to-zero, zero delta and duplicate account rejection, and verify they fail
- [ ] 1.6 Implement `Posting`, `Leg`, `AccountId`, `TransferReason` and `TransferResult` in `api.model` and verify the 1.5 tests pass
- [ ] 1.6a Write tests for the `Actor` record (factories `player(UUID)`, `console()`, `system()` and `plugin(name)`, where a blank name becomes `plugin:unknown`, with the stored ids `<uuid>`, `console`, `plugin:<name>` and `system`) and for each `LedgerOperation` record (`Payment`, `AdminGrant`, `AdminTake`, `AdminSet`, `StartingBalance`, `SeasonReset`, `TaskReward`): each builds its expected idempotency key and postings that sum to zero, and verify they fail
- [ ] 1.6b Implement the `Actor` record with its static factories, and the sealed `LedgerOperation` interface with its records, `postings(...)` and `IdempotencyKey`, all in `api.model`, and verify the 1.6a tests pass
- [ ] 1.7 Write `WalletService` unit tests with an in-memory `LedgerRepository` fake for pay validation branches (insufficient funds, zero, fractional, maximum amount, self, unknown recipient, balance cap) and verify they fail
- [ ] 1.8 Write `WalletService` unit tests for the cooldown with an advancing test clock and for admin give, take and set deltas, and verify they fail
- [ ] 1.9 Implement `LedgerRepository`, `WalletUseCase`, `WalletService` and `CooldownTracker` in `ports` and `core` and verify the 1.7 and 1.8 tests pass
- [ ] 1.10 Add the `economy` section to the configuration DTOs with a config round-trip test under `@TempDir`, first red then green, and verify the economy keys load with the documented defaults
- [ ] 1.11 Write a red config test that `economy.pay.confirmPercent` defaults to 50, accepts 0 and 100, and rejects 101 and -1 with an error naming the key, and verify it fails
- [ ] 1.12 Implement the `confirmPercent` key in the economy configuration DTOs and verify the 1.11 test passes
- [ ] 1.13 Run `.claude/skills/hexagonal-architecture/scripts/compile-core.sh` and verify api, ports and core compile without Bukkit, Guice or Jackson imports

## 2. PostgreSQL ledger adapter (delivery slice 18)

- [ ] 2.1 Write Testcontainers tests for `adapters/economy` with `postgres:16-alpine`: fresh schema per test, first `V1` migration creates the three ledger tables, verify they fail
- [ ] 2.2 Add `db/migration/economy/V1__create_economy_ledger.sql` with `economy_account`, `economy_transaction` (with the `kind TEXT NOT NULL` discriminator and nullable reference columns) and `economy_posting` and the history table `flyway_history_economy`, and verify the 2.1 migration test passes
- [ ] 2.2a Write converter tests: `MoneyConverter` round-trips minor units through `BIGINT`, and the entity mapper stores each `Actor` (player, console, plugin, system) as its id text in a TEXT column and reads it back equal, and verify they fail
- [ ] 2.2b Implement `MoneyConverter` in `adapters/persistence` (`@Converter(autoApply = true)`, next to the `SeasonIdConverter` from `add-season-lifecycle`) and the `Actor` TEXT mapping in the entity mapper of `adapters/economy`, and verify the 2.2a tests pass
- [ ] 2.2c Write mapping tests for `LedgerTransactionEntity` with `@Inheritance(strategy = SINGLE_TABLE)`: a `TaskReward` row reloads as its own subclass with `task_id`, and a second row with the same `@NaturalId` key raises Hibernate's `ConstraintViolationException`, and verify they fail
- [ ] 2.2d Implement the entities (`EconomyAccountEntity`, `LedgerTransactionEntity` with `@DiscriminatorColumn(name = "kind")` and one `@DiscriminatorValue` subclass per operation, `LedgerPostingEntity`), the entity to domain mapper, and the `economy` persistence unit with `hibernate.hbm2ddl.auto=validate`, and verify the 2.2c tests pass
- [ ] 2.3 Write adapter tests for `post`: applied, already applied for the same key, insufficient funds rolled back, cap exceeded rolled back, unknown account, and verify they fail
- [ ] 2.4 Implement the JPA `LedgerRepository` as one Unit of Work with the `@NaturalId` duplicate mapped to `AlreadyApplied`, ordered `PESSIMISTIC_WRITE` account loads, exact balance math in the `Account` aggregate, and the retry on `PessimisticLockException`, `LockTimeoutException` or SQLState 40P01/40001, and verify the 2.3 tests pass
- [ ] 2.5 Write the race test: 200 concurrent transfers in both directions between ten accounts from 16 threads released by one barrier, asserting no negative balance, unchanged sum and a clean audit, and verify it fails
- [ ] 2.6 Write the race test: eight threads posting the same idempotency key yield exactly one applied result, and verify it fails
- [ ] 2.7 Write the deadlock retry test with two explicit transactions forcing a 40P01 retry, and verify the balance changes once
- [ ] 2.8 Make the 2.5, 2.6 and 2.7 tests pass by the implementation from 2.4 and verify they are green
- [ ] 2.9 Write the season reset adapter test: a reset zeroes balances against `system:mint`, a second reset posts nothing and the audit stays clean, and verify it fails
- [ ] 2.10 Implement the season reset posting with keys `season-reset:<seasonId>:<accountId>` and verify the 2.9 test passes
- [ ] 2.10a Write a test that the season transition port from `add-season-lifecycle` triggers the reset for the ending season only, and verify it fails; then connect the transition to the wallet reset as a `SeasonTransitionListener` and verify it passes
- [ ] 2.10b Write a Guice test that the wallet reset is bound in the `SeasonTransitionListener` `Multibinder` in `platforms/common`, and that the season core iterates the bound listeners without naming economy types, and verify it fails
- [ ] 2.10c Implement the `Multibinder<SeasonTransitionListener>` registration of the wallet reset in `platforms/common` and verify the 2.10b test passes
- [ ] 2.11 Extend `PostgresAdapterMigrationTest` so the economy adapter migrates the same database as the other adapters without clashing, first red then green, and verify it passes
- [ ] 2.12 Write the ArchUnit rule `economyAdapterIsFrameworkFree` in `verification` (no Bukkit, Guice or `jakarta.inject` in `adapters/economy`; JPA is allowed there), verify it fails on a deliberately added Bukkit import in a scratch test, then keep it green on the real adapter
- [ ] 2.12a Run the JPA ArchUnit rules from `add-season-lifecycle` (`jakarta.persistence..` and `org.hibernate..` only in `..adapters..`, `@Entity` only in `..adapters..`, no `EntityManager#createNativeQuery`) against `adapters/economy` and verify they stay green

## 3. Balance, pay and audited admin commands (delivery slice 19)

- [ ] 3.1 Write `adapters/command` handler tests with `RecordingCommandSource` for `/balance`, `/bal`, `/pay` branches and the `Messages` fired for each, and verify they fail
- [ ] 3.2 Write handler tests for `/skygrid eco give|take|set|history` including the `confirm` requirement for set, no clamping on take and newest-first paging, and verify they fail
- [ ] 3.3 Write permission tests that every economy handler declares its permission and that admin subcommands are hidden from tab completion without `skygridng.economy.admin`, and verify they fail
- [ ] 3.4 Implement the economy command handlers and `Messages` additions in `adapters/command` and verify the 3.1 to 3.3 tests pass
- [ ] 3.5 Write MockBukkit tests in `platforms/common` for the in-flight guard (double `/pay` drops the second), the first-join starting balance and the recipient notification on success only, and verify they fail
- [ ] 3.6 Implement `EconomyModule`, `EconomyExecutor`, the in-flight set and the `EconomyFeature` wiring in `platforms/common` and verify the 3.5 tests pass
- [ ] 3.7 Add the permission nodes and `softDepend` entries to `plugin.yml` and verify a bootstrap test in `platforms/paper` lists them
- [ ] 3.8 Add the `/balance`, `/pay` and `/skygrid eco` section to the README, including the statement that coins are never sold for real money and the sentence "Granting coins, shop items or prestige for real money (store plugins, `eco give`, Vault deposits) violates the Minecraft EULA and is unsupported.", and verify the README contains both sentences
- [ ] 3.9 Write handler tests with `RecordingCommandSource` that `/pay` above `pay.confirmPercent` without `confirm` debits nothing and previews the balance after the payment, that `/pay` with `confirm` debits, that exactly 50 percent needs no confirm, and that `/balance` shows the reset date, and verify they fail
- [ ] 3.10 Implement the confirm gate in the `/pay` handler and the reset date in the `/balance` message and verify the 3.9 tests pass
- [ ] 3.11 Run `./gradlew :adapters:command:test :platforms:common:test --max-workers=2` and verify green

## 4. Currency task reward (delivery slice 20)

- [ ] 4.1 Write config tests for `type: currency` with amount "250" accepted and "2.5" or "0" rejected with an error naming the task, and verify they fail
- [ ] 4.2 Implement `RewardDto` currency parsing, the generated challenge schema entry and the `Reward.CurrencyGrant` guard, and verify the 4.1 tests pass
- [ ] 4.3 Write `RewardGrantor` and `TaskProgressService` tests asserting the `RewardKey` passed for a new completion and for an already recorded completion, and verify they fail
- [ ] 4.4 Implement the `RewardKey` parameter on `RewardGrantor#grant` and the currency branch that posts `task:<seasonId>:<challengeId>:<taskId>:<playerId>` on the economy executor, and verify the 4.3 tests pass
- [ ] 4.5 Write `CurrencyRewardReconciler` tests with a fake repository: a missing credit is re-posted once on join, a present key is a no-op, a changed amount tops up nothing, and verify they fail
- [ ] 4.6 Implement `CurrencyRewardReconciler` and wire it to the join event, and verify the 4.5 tests pass
- [ ] 4.7 Write a Testcontainers test that a completion for an offline player credits the balance once under the task key, and verify it fails
- [ ] 4.8 Make the 4.7 test pass and verify it is green

## 5. Vault economy provider (delivery slice 21)

- [ ] 5.1 Write tests with a fake `WalletUseCase` for Vault amount validation: 0.1 deposit fails, NaN and infinite fail, negative fails, and no balance changes, and verify they fail
- [ ] 5.2 Implement the exact `double` to `Money` conversion and the Vault failure responses in `platforms.common.economy.vault`, and verify the 5.1 tests pass
- [ ] 5.3 Write a test that a withdrawal re-checks the balance under the lock and fails when a cache says 100 but the database holds 40, and verify it fails
- [ ] 5.4 Implement the write path (`withdraw`, `deposit`) without the cache and verify the 5.3 test passes
- [ ] 5.5 Write a test that a successful Vault deposit writes one ledger transaction with actor `plugin:<name>` or `plugin:unknown`, and verify it fails
- [ ] 5.6 Implement the actor tagging and the `vault:<uuid>` key generation and verify the 5.5 test passes
- [ ] 5.7 Write MockBukkit tests with a stub Vault API on the classpath that both providers register at the configured priority and unregister on disable, and that a competing provider produces a warning naming it, and verify they fail
- [ ] 5.8 Implement `VaultRegistrar` (loaded only after `Class.forName` finds the legacy Economy class), the `BalanceCache` and the competing-provider warning, and verify the 5.7 tests pass
- [ ] 5.9 Write a bootstrap test in `platforms/paper` that the plugin enables without any Vault class and logs one info line, and verify it fails
- [ ] 5.10 Add the `compileOnly` Vault and VaultUnlocked dependencies in `platforms/common`, the `softDepend` entries, and make the 5.9 test pass
- [ ] 5.11 Write the ArchUnit rule `onlyVaultBindingDependsOnVault` in `verification`, verify it fails on a deliberate reference from `core`, and verify it passes on the real code

## 6. Ledger audit command (delivery slice 25)

- [ ] 6.1 Write adapter tests for `audit()` on a consistent ledger (no mismatch) and on a manually corrupted balance (named mismatch), and verify they fail
- [ ] 6.2 Implement `LedgerRepository#audit` returning the named mismatches and verify the 6.1 tests pass
- [ ] 6.3 Write a handler test for `/skygrid eco audit` that requires `skygridng.economy.admin` and prints each mismatch on its own line, and verify it fails
- [ ] 6.4 Implement the `audit` handler and verify the 6.3 test passes
- [ ] 6.5 Write adapter tests that the `console` actor of a give and the `plugin:<name>` actor of a Vault deposit are stored on their transactions and that `audit` counts the transactions per actor, and verify they fail
- [ ] 6.6 Implement the actor in the `history` output and the per-actor count in `audit` and verify the 6.5 test passes
- [ ] 6.7 Run the full `:adapters:economy:test` and `:verification:test` suites with `--max-workers=2` and verify green

## 7. Pull request

- [ ] 7.1 Open the pull request titled `feat(economy): add coin wallet with pay, task rewards and vault provider`, branched from the remote main branch after `add-season-lifecycle` and the Flyway fix are merged, and verify the PR title is a valid Conventional Commit
