# Spec Delta

## Purpose

Gives every player a season-scoped coin balance backed by an append-only, audited ledger, with payments
between players, audited admin adjustments, currency task rewards, a Vault economy provider and an
integrity check. Coins are a server-internal currency and are never sold for real money.

## ADDED Requirements

### Requirement: Whole-coin money
The system SHALL represent every coin amount as a non-negative whole number of minor units held in a 64-bit
integer, with zero fractional digits. Arithmetic on amounts MUST fail instead of overflowing or going negative.

#### Scenario: Addition overflows
- **WHEN** two amounts are added and the sum exceeds the 64-bit range
- **THEN** the operation fails and no amount is changed

#### Scenario: Subtraction below zero
- **WHEN** an amount is subtracted from a smaller amount
- **THEN** the operation fails and no negative amount is produced

### Requirement: Amount parsing
The system SHALL accept an amount typed by a player or an admin only as a plain decimal string of whole coins
greater than zero. It SHALL reject fractional, negative, zero, exponent or blank input and SHALL NOT round.

#### Scenario: Whole amount accepted
- **WHEN** the amount "42" is parsed
- **THEN** it is accepted as 42 coins

#### Scenario: Fractional amount rejected
- **WHEN** the amount "1.5" is parsed
- **THEN** it is rejected and the echoed value is "1.5"

#### Scenario: Exponent notation rejected
- **WHEN** the amount "1e3" is parsed
- **THEN** it is rejected

### Requirement: Starting balance on first join
The system SHALL create the account of a player who joins for the first time in the active season with the
configured starting balance, recorded as one ledger transaction keyed `start:<seasonId>:<playerId>`. A repeated
join SHALL NOT create a second starting transaction.

#### Scenario: First join in a season
- **WHEN** a player without an account in the active season joins
- **THEN** the account is created with the starting balance and one transaction keyed `start:<seasonId>:<playerId>` exists

#### Scenario: Rejoin in the same season
- **WHEN** a player who already has an account in the active season joins again
- **THEN** no new transaction is posted and the balance is unchanged

### Requirement: Own balance display
The system SHALL show a player their own balance, formatted with the configured singular or plural currency name,
when the player runs `/balance` or its alias `/bal`.

#### Scenario: Player checks own balance
- **WHEN** a player with 1 coin runs `/balance`
- **THEN** the message shows the balance with the singular currency name

### Requirement: Balance of another player
The system SHALL show the balance of another player only to a sender holding `skygridng.economy.balance.others`.

#### Scenario: Sender with permission
- **WHEN** a sender with `skygridng.economy.balance.others` runs `/balance <player>`
- **THEN** the message shows that player's balance in the active season

#### Scenario: Sender without permission
- **WHEN** a sender without `skygridng.economy.balance.others` runs `/balance <player>`
- **THEN** the command is refused and no balance is shown

### Requirement: Economy unavailable
If the database is unreachable, the system SHALL reply with a localized "economy unavailable" message and SHALL NOT
show a cached balance as the current balance.

#### Scenario: Database down during balance lookup
- **WHEN** a player runs `/balance` while the database is unreachable
- **THEN** the player sees the localized "economy unavailable" message and no balance value

### Requirement: Atomic payment
The system SHALL execute `/pay <player> <amount>` as one database transaction that debits the payer and credits the
recipient, or changes neither balance.

#### Scenario: Successful payment
- **WHEN** a payer with enough coins pays a recipient who has joined the server
- **THEN** the payer is debited and the recipient is credited by the same amount in one transaction

#### Scenario: Failure during payment
- **WHEN** the database fails after the debit but before the commit
- **THEN** neither balance changes

### Requirement: Payment rejections for funds
The system SHALL reject a payment whose amount exceeds the payer's balance and SHALL echo the amount and the payer's
current balance.

#### Scenario: Insufficient funds
- **WHEN** a payer with 5 coins runs `/pay <player> 10`
- **THEN** the payment is rejected, the message echoes 10 and 5, and both balances are unchanged

### Requirement: Payment amount limits
The system SHALL reject a payment whose amount is zero, negative, fractional or above `economy.pay.maxAmount`, and
SHALL echo the rejected value.

#### Scenario: Amount above the configured maximum
- **WHEN** a payer runs `/pay <player> <amount>` with an amount above `economy.pay.maxAmount`
- **THEN** the payment is rejected and the message echoes the amount

#### Scenario: Zero amount
- **WHEN** a payer runs `/pay <player> 0`
- **THEN** the payment is rejected

### Requirement: Payment recipient rules
The system SHALL reject a payment to the payer themself or to a player who has never joined the server.

#### Scenario: Pay yourself
- **WHEN** a player runs `/pay <own name> 1`
- **THEN** the payment is rejected and no balance changes

#### Scenario: Recipient never joined
- **WHEN** a player pays a name that has no account
- **THEN** the payment is rejected and no balance changes

### Requirement: Balance cap for payments
The system SHALL reject a payment that would raise the recipient's balance above `economy.maxBalance`, and SHALL change
neither balance.

#### Scenario: Recipient would exceed the cap
- **WHEN** a payment would raise the recipient above `economy.maxBalance`
- **THEN** the payment is rejected and both balances are unchanged

### Requirement: Payment cooldown
While a payer's pay cooldown (`economy.pay.cooldown`) is running, the system SHALL reject a further `/pay` and SHALL
show the remaining time. The cooldown SHALL be measured with the injected clock.

#### Scenario: Second payment within the cooldown
- **WHEN** a payer pays again 1 second after a successful payment with a 2 second cooldown
- **THEN** the second payment is rejected and the message shows the remaining time

#### Scenario: Payment after the cooldown
- **WHEN** a payer pays again after the cooldown has elapsed
- **THEN** the payment is processed

### Requirement: Recipient notification
When a payment succeeds and the recipient is online, the system SHALL notify the recipient in chat. The system SHALL NOT
notify the recipient when the payment is rejected.

#### Scenario: Online recipient
- **WHEN** a payment succeeds and the recipient is online
- **THEN** the recipient receives a chat message naming the amount and the payer

#### Scenario: Rejected payment
- **WHEN** a payment is rejected
- **THEN** no message is sent to the recipient

### Requirement: Admin give and take
When an admin holding `skygridng.economy.admin` runs `/skygrid eco give|take <player> <amount> [reason]`, the system SHALL
post exactly one ledger transaction against the `system:mint` account. The transaction SHALL record the acting admin
(the admin UUID or `console`) and the reason.

#### Scenario: Give coins
- **WHEN** an admin gives 10 coins to a player with a reason
- **THEN** one transaction credits the player 10 and debits `system:mint` 10, with the admin and reason recorded

#### Scenario: Take coins
- **WHEN** an admin takes 10 coins from a player
- **THEN** one transaction debits the player 10 and credits `system:mint` 10

### Requirement: Admin take without clamping
The system SHALL reject an admin take that exceeds the player's balance, echo the balance, and SHALL NOT clamp the
amount to the balance.

#### Scenario: Take more than the balance
- **WHEN** an admin takes 50 coins from a player with 20 coins
- **THEN** the take is rejected, the message echoes 20, and the balance is unchanged

### Requirement: Admin set with confirmation
The system SHALL set a player's balance only when the admin runs `/skygrid eco set <player> <amount> confirm`. The system
SHALL post the difference between the old and the new balance as one audited transaction.

#### Scenario: Set without confirmation
- **WHEN** an admin runs `/skygrid eco set <player> <amount>` without `confirm`
- **THEN** nothing is changed and the message explains the confirm keyword

#### Scenario: Confirmed set
- **WHEN** an admin runs the set command with `confirm` and the player has 30 coins, set to 50
- **THEN** one transaction credits the player 20 with reason `ADMIN_SET`

### Requirement: Transaction history
The system SHALL list a player's ledger entries newest first, one page at a time, with time, reason, delta, balance after,
counterparty and actor, for an admin running `/skygrid eco history <player> [page]`.

#### Scenario: Paged history
- **WHEN** an admin requests page 2 of a player's history
- **THEN** the entries of the second page are listed newest first with all six fields

### Requirement: Append-only ledger
The system SHALL NOT update or delete any ledger transaction or posting. A correction SHALL be a new opposite transaction.

#### Scenario: Correcting a mistaken give
- **WHEN** an admin reverses a wrong give
- **THEN** a new transaction debits the same account by the same amount and the original rows remain unchanged

### Requirement: Balanced postings
The system SHALL reject any transaction whose postings do not sum to zero, contain a zero delta, or name the same account twice.

#### Scenario: Unbalanced transaction
- **WHEN** a transaction is built whose postings sum to 1
- **THEN** the transaction is rejected before it reaches the database

### Requirement: Non-negative player balances
The system SHALL reject a transaction that would make a player balance negative or above `economy.maxBalance`, and SHALL
roll back all of its postings.

#### Scenario: Debit below zero
- **WHEN** a transaction would leave a player balance at -1
- **THEN** the transaction is rejected and no account or posting changes

### Requirement: Idempotent transactions
The system SHALL apply a transaction at most once per idempotency key. A repeated key SHALL return the original transaction
and SHALL NOT change any balance.

#### Scenario: Same key from concurrent threads
- **WHEN** eight threads post a transaction with the same idempotency key at the same time
- **THEN** exactly one result is applied and the others report the existing transaction

#### Scenario: Retry after a deadlock
- **WHEN** a transaction fails with a deadlock and is retried with the same key
- **THEN** the balance changes exactly once

### Requirement: Concurrent transfers keep money conserved
The system SHALL keep the sum of all account balances at zero under concurrent transfers, and SHALL NOT let a player
balance go negative because of concurrency.

#### Scenario: Concurrent transfers in both directions
- **WHEN** 200 transfers between ten accounts run concurrently from 16 threads
- **THEN** every call returns, no player balance is negative, the sum of balances is unchanged and the audit reports no mismatch

### Requirement: Season-scoped accounts and entries
The system SHALL store every account, transaction and posting with the season id of the season in which it was posted.

#### Scenario: Balance in a new season
- **WHEN** a player has 100 coins in season A and season B starts
- **THEN** the player's season B account starts at the starting balance and the season A entries remain in season A

### Requirement: Season reset
When a season ends, the system SHALL post one `SEASON_RESET` transaction for each player account of that season with a
non-zero balance, zeroing that balance against `system:mint`. The transaction key SHALL be `season-reset:<seasonId>:<accountId>`.

#### Scenario: Reset of a non-zero balance
- **WHEN** a season ends and a player has 75 coins
- **THEN** one transaction debits the player 75, credits `system:mint` 75 and keeps the history of that season

#### Scenario: Repeated reset
- **WHEN** the reset of the same season runs a second time
- **THEN** no transaction is posted and the audit stays clean

### Requirement: Currency task reward configuration
The system SHALL accept `type: currency` with `amount: "<whole coins>"` as a task reward in `challenges/<id>.<ext>` and in
the generated challenge schema, and SHALL reject a currency reward whose amount is zero or not a whole number of coins.

#### Scenario: Valid currency reward
- **WHEN** a challenge file contains a currency reward with amount "250"
- **THEN** the challenge loads with a currency reward of 250 coins

#### Scenario: Fractional currency reward
- **WHEN** a challenge file contains a currency reward with amount "2.5"
- **THEN** the challenge file is rejected with an error naming the task

### Requirement: Currency task reward credit
When a task completion is newly recorded and its reward is a currency reward, the system SHALL credit the completing player
with idempotency key `task:<seasonId>:<challengeId>:<taskId>:<playerId>`, also when the player is offline.

#### Scenario: Offline completion
- **WHEN** a task with a currency reward completes for a player who is offline
- **THEN** the player's balance is credited once with the reward amount under the task key

#### Scenario: Completion already recorded
- **WHEN** the same task completion is reported again
- **THEN** no second credit is posted

### Requirement: Missing currency credit repair
If a task completion is recorded but its currency credit is missing, the system SHALL apply the credit on the player's next
join. A key that already exists SHALL be a no-op. Changing a reward amount later SHALL NOT top up completions already paid.

#### Scenario: Crash between completion and credit
- **WHEN** a player's completion is recorded but the credit was lost in a crash, and the player joins
- **THEN** the credit is posted once with the original key

#### Scenario: Reward amount changed later
- **WHEN** a configured currency reward amount is raised after a completion was paid
- **THEN** the already paid completion receives no additional coins

### Requirement: Vault provider registration
Where Vault or VaultUnlocked is installed, the system SHALL register a provider for the legacy
`net.milkbowl.vault.economy.Economy` service and, when present, the VaultUnlocked `net.milkbowl.vault2.economy.Economy`
service, each at the configured service priority.

#### Scenario: Vault installed
- **WHEN** Vault is installed and the plugin enables
- **THEN** the legacy economy service is registered at the configured priority and unregistered on disable

### Requirement: Vault absent
Where neither Vault nor VaultUnlocked is installed, the system SHALL start normally, log one info line, and keep all other
economy features working.

#### Scenario: No Vault on the server
- **WHEN** the plugin enables without any Vault class
- **THEN** it starts, logs one info line and `/balance` and `/pay` still work

### Requirement: Vault amount validation
When a Vault call passes an amount that is negative, not finite, or not a whole number of coins, the system SHALL return a
failure response and SHALL change no balance.

#### Scenario: Fractional deposit
- **WHEN** a plugin deposits 0.1 coins through Vault
- **THEN** the response is a failure and no balance changes

#### Scenario: NaN withdrawal
- **WHEN** a plugin withdraws NaN through Vault
- **THEN** the response is a failure and no balance changes

### Requirement: Vault writes are authoritative
The system SHALL re-check a player's balance under the database row lock for every Vault withdrawal, even when a cached
balance suggests enough coins.

#### Scenario: Stale cached balance
- **WHEN** a cached balance shows 100 coins but the database holds 40 and a plugin withdraws 60
- **THEN** the withdrawal fails and the database balance stays 40

### Requirement: Vault transactions are ledgered
Every successful Vault deposit or withdrawal SHALL be one ledger transaction whose actor is `plugin:<name>` when the calling
plugin is identified, and `plugin:unknown` otherwise.

#### Scenario: Identified caller
- **WHEN** plugin "Shop" deposits 5 coins and identifies itself
- **THEN** one transaction with actor `plugin:Shop` credits the account 5

### Requirement: Competing Vault economy warning
When another economy provider is already registered with Vault at the configured priority, the system SHALL log a warning
that names the competing provider.

#### Scenario: Competing provider present
- **WHEN** another economy provider is registered before SkyGrid enables
- **THEN** a warning naming that provider is logged

### Requirement: Economy permissions
The system SHALL declare `skygridng.economy.balance`, `skygridng.economy.pay` (default true), `skygridng.economy.balance.others`
and `skygridng.economy.admin` (both operator-only), and every economy command SHALL declare the permission that gates it.

#### Scenario: Tab completion gated
- **WHEN** a sender without `skygridng.economy.admin` requests tab completion for `/skygrid eco`
- **THEN** the admin subcommands are not suggested

### Requirement: Economy command labels
The system SHALL expose `/balance`, `/bal` and `/pay` as root labels only when `economy.commands.rootLabels` is enabled, and
SHALL always expose them under `/skygrid eco`.

#### Scenario: Root labels disabled
- **WHEN** `economy.commands.rootLabels` is false
- **THEN** `/pay` is not registered as a root label and `/skygrid eco pay` still works

### Requirement: In-flight economy operations
While a player has an economy operation in flight, the system SHALL drop further economy commands and clicks from that player and
show an action-bar hint.

#### Scenario: Double submission
- **WHEN** a player sends `/pay` twice before the first operation finishes
- **THEN** the second submission is dropped with a hint and only one payment is attempted

### Requirement: Economy audit
The system SHALL report, for `/skygrid eco audit`, whether every transaction's postings sum to zero and whether every account
balance equals the sum of its postings, naming each mismatch. The command SHALL require `skygridng.economy.admin`.

#### Scenario: Clean ledger
- **WHEN** an admin runs the audit on a consistent ledger
- **THEN** the report states that no mismatch was found

#### Scenario: Corrupted balance
- **WHEN** an account balance differs from the sum of its postings
- **THEN** the report names that account as a mismatch

### Requirement: No real-money coins
The plugin SHALL NOT offer any command, GUI or API that converts coins into real-world money. The README SHALL state that
coins are never sold for real money.

#### Scenario: README statement
- **WHEN** the README is read
- **THEN** it states that coins are never sold for real money
