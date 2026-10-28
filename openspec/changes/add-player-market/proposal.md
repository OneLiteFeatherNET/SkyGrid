# Proposal

Ships under the Conventional Commits type and scope `feat(market)`. Depends on `add-admin-shop`, whose mailbox
receives every item the market delivers, and transitively on `add-player-wallet`.

## Why

Players can buy from fixed shops but cannot sell to each other, so no player-driven market forms and
items from one player's world never reach another's. This change adds a player marketplace where a seller
escrows an item, sets a fixed price and a buyer pays through the ledger, with a sales tax that removes coins
from circulation (epic J1, delivery slice 24).

## What Changes

- Add `player-market`: `/market sell <price>` moves the held stack into escrow and creates a listing for 48 hours.
  A player may hold at most 10 listings. Prices stay within configured bounds and the serialized item within a size cap.
- Add the market GUI (`/market`) that pages listings newest first, filters by item id with `/market search <item>`,
  and shows price, tax and the seller's net before a seller confirms a listing.
- Buying a listing is one database transaction: the buyer is debited, the seller is credited the price minus a
  5 percent tax, the tax is posted to `system:market_tax` (removed from circulation), the listing is marked sold and
  the item is queued in the buyer's mailbox. Exactly one of two concurrent buyers succeeds.
- Cancelling a listing and listing expiry (after 48 hours) return the item to the seller's mailbox.
- At season end, every active or pending listing closes into the ending season's mailbox.
- Add the `V3` migration for `market_listing` in the economy migration folder.

No **BREAKING** changes. The market is new and no existing behavior changes.

## Capabilities

### New Capabilities

- `player-market`: player-to-player listings with item escrow, fixed prices, the sales tax, purchase, cancel and
  expiry, and the listing caps and bounds that keep the market fair.

### Modified Capabilities

None. Mailbox delivery is reused from `item-mailbox` without changing its requirements.

## Impact

- Modules: `api` (`Listing`, `ListingState`, market settings), `ports` (`MarketUseCase`, `MarketRepository`),
  `core` (`MarketService`, tax calculation, `CooldownTracker` reuse), `adapters/economy` (market repository,
  `V3` migration), `adapters/command` (`MarketCommands` for sell, search and page), `platforms/common` (market view,
  listing confirm view, expiry task, season-end closure hook).
- Configuration: `economy.market` section (tax basis points, duration, max listings, price bounds, maximum item bytes).
- Persistence: `V3__create_market.sql`, same economy history table.
- Prerequisites: `add-admin-shop` merged (mailbox), `add-player-wallet` merged (ledger), `add-season-lifecycle`.
