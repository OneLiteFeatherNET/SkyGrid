# Spec Delta

## Purpose

Delivers items that a player bought, sold back or got returned from the player market into the player's inventory,
without losing or duplicating items when the server or the database fails between the money change and the item
movement. Undeliverable items wait in a per-season mailbox until the player can claim them.

## ADDED Requirements

### Requirement: Delivery is recorded with its payment
The system SHALL record every item delivery that follows a payment as a mailbox row in the same database transaction as
the payment's ledger postings, so that the money and the pending item are committed together or not at all.

#### Scenario: Purchase queues a delivery
- **WHEN** a purchase debits the buyer and queues an item
- **THEN** the debit and the pending mailbox row are committed in one transaction

#### Scenario: Failure after the debit
- **WHEN** the database fails after the debit but before the mailbox row is written
- **THEN** neither the debit nor the mailbox row is committed

### Requirement: Immediate delivery to an online player
When a delivery is queued for an online player whose inventory has room for the whole stack, the system SHALL deliver the
stack immediately and mark the mailbox row as delivered.

#### Scenario: Room for the whole stack
- **WHEN** a purchased stack of 16 items is queued and the player has room for 16
- **THEN** the stack is added to the inventory and the row is marked delivered

### Requirement: Whole-stack rule
If the inventory has no room for the whole stack, the system SHALL keep the stack in the mailbox, SHALL NOT deliver a part
of it, and SHALL tell the player to run `/market claim`.

#### Scenario: Full inventory
- **WHEN** a delivery of 16 items is queued and the player has room for only 4
- **THEN** no item is added, the row stays pending, and the player is told to run `/market claim`

### Requirement: Claiming pending items
When a player runs `/market claim`, the system SHALL move each pending delivery that fits into the inventory and SHALL keep
the others in the mailbox.

#### Scenario: Partial claim
- **WHEN** a player with two pending stacks runs `/market claim` and room exists for only one
- **THEN** one stack is delivered and the other remains pending

### Requirement: Reconciliation on join
When a player joins, the system SHALL reconcile that player's pending deliveries and pending item movements before it delivers anything new. After reconciliation, when the player has pending mailbox items, the system SHALL tell the player the pending item count and the absolute season end date at which unclaimed items expire.

#### Scenario: Join with pending deliveries
- **WHEN** a player with pending deliveries joins
- **THEN** reconciliation runs first and new deliveries follow only after it completes

#### Scenario: Join with pending items
- **WHEN** a player with 2 pending items joins during season `s02`
- **THEN** the player is told the count 2 and the absolute end date of `s02`

### Requirement: Inventory journal
Every movement of a stack between a player's inventory and the database SHALL write a journal entry, stored in the player's data
file, in the same main-thread tick as the inventory change, and the system SHALL save the player data immediately afterwards.

#### Scenario: Item leaves the inventory
- **WHEN** a listed or sold stack is removed from the inventory
- **THEN** its journal entry is written in the same tick and the player data is saved

### Requirement: Journal reconciliation for pending movements
On join, the system SHALL finish a pending movement whose id is in the journal, and SHALL discard a pending movement whose id is
not in the journal.

#### Scenario: Crash after the item left the inventory
- **WHEN** a pending sale intent has its id in the journal at join
- **THEN** the sale is completed and the credit is posted once

#### Scenario: Crash before the item left the inventory
- **WHEN** a pending sale intent has no id in the journal at join
- **THEN** the intent is discarded and the items remain in the inventory

### Requirement: Journal reconciliation for deliveries
On join, the system SHALL mark a pending delivery as delivered when its id is in the journal, and SHALL deliver it again when its id
is not in the journal.

#### Scenario: Crash after delivery saved
- **WHEN** a pending delivery id is in the journal at join
- **THEN** the delivery is marked delivered and the item is not added a second time

#### Scenario: Crash before delivery saved
- **WHEN** a pending delivery id is not in the journal at join
- **THEN** the item is delivered once and the row is marked delivered

### Requirement: Bounded journal
The system SHALL keep at most 64 journal entries per player and SHALL drop entries that have no matching open row during reconciliation.

#### Scenario: Stale entries
- **WHEN** a journal holds an id with no open row
- **THEN** that entry is dropped at reconciliation

### Requirement: Verification before a movement
Before it removes a stack for a listing or a sale, the system SHALL verify that the stack still equals the snapshot taken when the operation started. If the
verification fails, the system SHALL discard the pending row and SHALL change nothing else.

#### Scenario: Item moved meanwhile
- **WHEN** the stack was moved out of the inventory after the snapshot
- **THEN** the pending row is discarded and no balance or item changes

### Requirement: Season end for mailbox items
The system SHALL expire pending mailbox items with the season, under the inventory policy of `add-season-lifecycle`, and SHALL NOT carry them into the next season. An expiry is a loss, so the system SHALL communicate it before the season end: on join (pending count and absolute end date), in every season announcement (which states the player's own pending count), and on the shop buy confirm view in the final 24 hours.

#### Scenario: Season ends with pending items
- **WHEN** a season ends and a player has pending mailbox items
- **THEN** those items are not delivered in the next season

#### Scenario: Expiry communicated in advance
- **WHEN** a player has 3 pending mailbox items during the final 7 days of the season
- **THEN** the season announcements and the join message state the count 3 and the absolute season end date

### Requirement: Mailbox is season scoped
The system SHALL store every mailbox row and sale intent with its season id.

#### Scenario: Pending item in an earlier season
- **WHEN** a season B starts while a season A row is still pending
- **THEN** the row is not visible to the season B mailbox

### Requirement: Fully delivered stacks stay intact
The system SHALL deliver each stack as one unit with its original item data, and SHALL NOT alter, split or merge it during delivery.

#### Scenario: Enchanted stack delivered
- **WHEN** a delivered stack carries custom data
- **THEN** the inventory receives the stack with the same data
