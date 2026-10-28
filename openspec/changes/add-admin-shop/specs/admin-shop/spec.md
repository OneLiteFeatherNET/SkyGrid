# Spec Delta

## Purpose

Lets server admins define fixed-price shops in configuration files and lets players buy items from them and sell
matching items back for coins, through a chest GUI. Prices are never arbitrage-able across shops, and item
movements are crash safe.

## ADDED Requirements

### Requirement: Shop files
The system SHALL load each shop from its own file `shops/<id>.<ext>` in JSON, YAML or TOML, and SHALL publish a generated
JSON schema for shop files. A bundled default shop SHALL be provided.

#### Scenario: Default shop present
- **WHEN** the plugin starts with no shop files
- **THEN** the bundled `general` shop is loaded

#### Scenario: Shop in TOML
- **WHEN** `shops/tools.toml` contains a valid shop
- **THEN** the shop `tools` is available with its entries

### Requirement: Shop entry shape
The system SHALL accept a shop entry with an item id, a quantity, a slot and at least one of a buy price and a sell price,
both given as whole-coin strings. An entry with only a sell price SHALL be a sell-only entry.

#### Scenario: Sell-only entry
- **WHEN** an entry has a sell price but no buy price
- **THEN** the entry is shown with no buy action and can be sold to

#### Scenario: Entry without any price
- **WHEN** an entry has neither a buy price nor a sell price
- **THEN** the shop file is rejected with an error naming the entry

### Requirement: Unknown item disables an entry
If an entry names an item id that the platform does not know, the system SHALL disable that entry, SHALL log a warning, and SHALL
NOT show a placeholder item in its place.

#### Scenario: Unknown item id
- **WHEN** a shop entry names an unknown item id
- **THEN** the entry is disabled, a warning is logged, and the GUI shows no item for it

### Requirement: Arbitrage rule at load
The system SHALL reject a shop file at load when an entry's unit sell price is higher than the lowest unit buy price of the same item
in any loaded shop, and the error SHALL name both entries.

#### Scenario: Sell price above a buy price elsewhere
- **WHEN** shop A buys cobblestone at 2 coins per unit and shop B sells cobblestone at 3 coins per unit to players
- **THEN** the file that contains the sell entry is rejected and the error names both entries

#### Scenario: Equal prices accepted
- **WHEN** the unit sell price equals the lowest unit buy price of the same item
- **THEN** the file is accepted

### Requirement: Shop view
When a player runs `/shop [id]`, the system SHALL open a chest GUI that shows each entry's item, quantity, buy price, sell price and the
player's current balance. Without an id the default shop SHALL open.

#### Scenario: Open a shop
- **WHEN** a player runs `/shop tools`
- **THEN** a GUI shows the entries of `tools` with their prices and the player's balance

#### Scenario: Unknown shop id
- **WHEN** a player runs `/shop missing`
- **THEN** the command reports that the shop does not exist and opens nothing

### Requirement: Buying from a shop
When a player clicks buy on an entry, the system SHALL debit the price and queue the item in the player's mailbox in one database transaction,
and SHALL refuse the purchase when the balance is lower than the price.

#### Scenario: Successful purchase
- **WHEN** a player with enough coins buys an entry
- **THEN** the player is debited the price and the item is queued for delivery in the same transaction

#### Scenario: Insufficient funds
- **WHEN** a player with fewer coins than the price clicks buy
- **THEN** nothing is debited, nothing is queued and the GUI shows the balance

### Requirement: Concurrent purchases respect the balance
When two purchases by the same player run at the same time and their total exceeds the balance, the system SHALL complete exactly
the purchases that the balance covers and SHALL NOT overdraw the account.

#### Scenario: Double click on a costly entry
- **WHEN** two buy clicks for a price of 60 arrive at the same time with a balance of 100
- **THEN** exactly one purchase succeeds and the balance is 40

### Requirement: Selling to a shop
When a player clicks sell on an entry, the system SHALL remove exactly the configured quantity of matching items from the inventory and
credit the sell price, and SHALL refuse the sale when the inventory holds fewer matching items.

#### Scenario: Successful sale
- **WHEN** a player holding 64 cobblestone sells a 64 entry for 16 coins
- **THEN** 64 cobblestone are removed and the player is credited 16 coins once

#### Scenario: Too few items
- **WHEN** a player holds 10 cobblestone and sells a 64 entry
- **THEN** the sale is refused and nothing is removed or credited

### Requirement: Only pristine items sell
The system SHALL accept for sale only stacks equal to a fresh stack of the entry's item, with no custom name, enchantment, lore, damage or other data.

#### Scenario: Renamed item
- **WHEN** a player sells an item of the entry's type that has a custom name
- **THEN** that stack is not counted for the sale

### Requirement: Crash-safe sale
The system SHALL remove the items and credit the coins as one recoverable sequence: a pending sale intent is written first, the items are removed and
journaled, and the credit with key `shopsale:<intentId>` is posted when the intent is committed. Reconciliation SHALL apply the credit at most once.

#### Scenario: Crash after items were removed
- **WHEN** the server stops after the items were removed and before the credit was committed
- **THEN** on the player's next join the credit is posted once and the intent is marked committed

### Requirement: No sales in creative or spectator mode
The system SHALL refuse shop sales while the player is in creative or spectator mode.

#### Scenario: Sell in creative mode
- **WHEN** a player in creative mode attempts to sell
- **THEN** the sale is refused and no item is removed

### Requirement: Shop GUI cannot move items
The system SHALL cancel every click and drag that touches a shop view, and SHALL show display items that are copies and never become inventory items, so the GUI cannot be used to duplicate or lose items.

#### Scenario: Shift click in the shop view
- **WHEN** a player shift-clicks an item in the shop view
- **THEN** no item moves and no balance changes

### Requirement: Shop permission
The system SHALL gate `/shop` with `skygridng.economy.shop`, which is granted to all players by default.

#### Scenario: Permission removed
- **WHEN** a player without `skygridng.economy.shop` runs `/shop`
- **THEN** the command is refused
