# Proposal

Ships under the Conventional Commits type and scope `feat(shop)`. Depends on `add-player-wallet`, which
provides the ledger, the season scope and the `system:shop` account.

## Why

Players can earn coins through the wallet but have no fixed place to turn loot into coins or coins into
blocks they lack. Item delivery is also unsafe on its own: a purchase or sale that touches both the
database and a player's inventory can duplicate or lose items after a crash. This change adds a
configurable admin shop and the crash-safe mailbox that every item delivery in the economy will use
(epic J1, delivery slices 22 and 23).

## What Changes

- Add `item-mailbox`: a mailbox of undelivered items per player and season, stored in PostgreSQL. A
  purchase or any other delivery writes a mailbox row in the same transaction as its money posting.
- Add an inventory journal stored in the player data file (PDC). Every item movement between an inventory
  and the database writes a journal entry in the same main-thread tick and saves the player data. Join
  reconciliation finishes or discards half-finished movements.
- Add `/market claim` as the command that moves pending mailbox items into the inventory when there is no
  room at the time of delivery.
- Add `admin-shop`: shops loaded from `shops/<id>.<ext>` with a generated JSON schema in `schema/`, one
  bundled default shop `shops/general.yml`, and a chest GUI opened by `/shop [id]`.
- Buy: debit the price and queue the item in the mailbox in one transaction.
- Sell: remove exactly the configured quantity of pristine matching items and credit the sell price,
  crash safe through a sale intent.
- Reject a shop file at load when an entry's unit sell price is above the lowest unit buy price of the same
  item in any shop (no arbitrage loop).
- Refuse shop sells while the player is in creative or spectator mode.
- Add the `V2` migration for `mailbox_delivery` and `shop_sale_intent` in the economy migration folder.

No **BREAKING** changes. The shop and mailbox are new; no existing behavior changes.

## Capabilities

### New Capabilities

- `admin-shop`: admin-defined shops with fixed buy and sell prices, shown in a GUI, the buy and sell
  transactions and the load-time price rule.
- `item-mailbox`: the delivery of items bought or returned to a player, the mailbox that holds undeliverable
  items, the inventory journal that keeps item movements consistent with the ledger across crashes.

### Modified Capabilities

None. The ledger requirements of `player-wallet` are reused without change.

## Impact

- Modules: `api` (`ItemPayload`, `ShopEntry`, `ShopCatalog`, `Delivery`), `ports` (`ShopUseCase`,
  `MailboxUseCase`, `ShopRepository`, `MailboxRepository`, `EconomyViews`), `core` (`ShopService`,
  `MailboxService`), `adapters/economy` (mailbox and sale intent repositories, `V2` migration),
  `adapters/config` (`ShopDtos`, `JacksonShopRepository`, `shops/general.yml`, `schema/shop.schema.json`),
  `adapters/command` (`ShopCommands`, `/market claim`), `platforms/common` (shop, mailbox and confirm views,
  item serializer, inventory journal, join reconciler, Bukkit item resolution).
- Persistence: `V2__create_mailbox_and_shop.sql`, same economy history table.
- Prerequisites: `add-player-wallet` merged; `add-season-lifecycle` for season ids and the season end that
  expires mailbox items with the inventory.
