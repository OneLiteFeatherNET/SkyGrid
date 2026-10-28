# Spec Delta

## Purpose

Lets players list items from their inventory at a fixed price and lets other players buy them with coins. Items are
held in escrow while listed, a sales tax removes coins from circulation, and every purchase, cancel and expiry
resolves to exactly one outcome, so no item or coin is duplicated or lost.

## ADDED Requirements

### Requirement: Creating a listing
When a player runs `/market sell <price>`, the system SHALL move the held stack into escrow and create an active listing that expires 48 hours after its creation. The system SHALL show each seller the absolute expiry date of each of the seller's own listings.

#### Scenario: Listing a stack
- **WHEN** a player holding 16 iron ingots runs `/market sell 40`
- **THEN** the stack leaves the inventory into escrow and an active listing with price 40 and expiry 48 hours later exists

#### Scenario: Listing preview
- **WHEN** a player opens the listing confirm view for a price of 100
- **THEN** the view shows the price, the 5 percent tax of 5, the seller's net of 95 and the absolute expiry date before the seller confirms

#### Scenario: Listing preview when the season ends first
- **WHEN** a player opens the listing confirm view and the season ends before the 48 hour expiry
- **THEN** the view states the absolute season end date, states that the listing closes at that date, and states that the item is not returned

#### Scenario: Seller views own listing expiry
- **WHEN** a seller opens the view of their own listings
- **THEN** each listing shows its absolute expiry date

### Requirement: Listing limits
The system SHALL reject a listing when the seller already has 10 listings in the pending or active state, when the price is outside
the configured minimum and maximum, or when the serialized item exceeds `economy.market.maxItemBytes`, and SHALL keep the item in the
seller's hand.

#### Scenario: Eleventh listing
- **WHEN** a seller with 10 listings runs `/market sell`
- **THEN** the listing is rejected and the item stays in the seller's hand

#### Scenario: Price out of bounds
- **WHEN** a seller lists at a price below the minimum
- **THEN** the listing is rejected and the item stays in the seller's hand

#### Scenario: Concurrent listings at the cap
- **WHEN** a seller with 9 listings runs two sell commands at the same time
- **THEN** at most one listing is created and the seller ends with 10 listings

### Requirement: Listing refused in creative or spectator mode
The system SHALL refuse to list items while the seller is in creative or spectator mode.

#### Scenario: Creative listing
- **WHEN** a seller in creative mode runs `/market sell`
- **THEN** the listing is refused and no item leaves the inventory

### Requirement: Browsing the market
The system SHALL show the active listings in the market GUI newest first, in pages, and SHALL filter them by item id for
`/market search <item>`.

#### Scenario: Search by item id
- **WHEN** a player runs `/market search minecraft:iron_ingot`
- **THEN** the GUI shows only active listings of that item, newest first

### Requirement: Atomic purchase
When a player buys an active listing, the system SHALL in one database transaction verify that the listing is still active and not expired,
debit the buyer the listed price, credit the seller the price minus the tax, credit the tax to `system:market_tax`, mark the listing sold, and
queue the item in the buyer's mailbox.

#### Scenario: Successful purchase of a listing for 40
- **WHEN** a buyer with at least 40 coins buys an active listing for 40 with a 5 percent tax
- **THEN** the buyer is debited 40, the seller is credited 38, `system:market_tax` is credited 2, the listing is sold and the item is queued for the buyer

#### Scenario: Tax rounds down
- **WHEN** a listing priced at 1 coin is bought
- **THEN** the tax is 0 and the seller is credited 1

### Requirement: Single winner for concurrent purchases
If two players buy the same listing at the same time, the system SHALL complete exactly one purchase, SHALL tell the other buyer that the listing is already sold, and SHALL change
the other buyer's balance by nothing.

#### Scenario: Double buy
- **WHEN** two buyers click buy on the same listing at the same moment
- **THEN** exactly one purchase succeeds and the other buyer's balance is unchanged

### Requirement: Buying an own listing
The system SHALL refuse a purchase by the seller of the listing.

#### Scenario: Seller buys own listing
- **WHEN** a seller clicks buy on their own listing
- **THEN** the purchase is refused and no balance changes

### Requirement: Insufficient funds for a purchase
The system SHALL refuse a purchase whose price is higher than the buyer's balance and SHALL leave the listing active.

#### Scenario: Buyer too poor
- **WHEN** a buyer with 10 coins clicks buy on a listing for 40
- **THEN** the purchase is refused, the listing stays active and no balance changes

### Requirement: Cancelling a listing
When a seller cancels an active listing, the system SHALL close the listing and queue its item in the seller's mailbox.

#### Scenario: Cancel active listing
- **WHEN** a seller cancels an active listing
- **THEN** the listing is cancelled and the item is queued in the seller's mailbox

### Requirement: Cancel and purchase race
If a cancel and a purchase of the same listing run at the same time, the system SHALL resolve them to exactly one outcome: either the purchase wins and the cancel is refused,
or the cancel wins and the purchase is refused. The item SHALL be delivered exactly once.

#### Scenario: Cancel and buy at the same moment
- **WHEN** the seller cancels and a buyer buys the same listing at the same moment
- **THEN** exactly one of the two succeeds and exactly one mailbox row is created for the item

### Requirement: Listing expiry
The system SHALL expire every active listing whose expiry has passed, return its item to the seller's mailbox and remove the listing from the market.

#### Scenario: Listing expires
- **WHEN** an active listing's 48 hours have passed
- **THEN** the listing is expired and its item is queued in the seller's mailbox

### Requirement: Expiry race
If an expiry and a purchase of the same listing run at the same time, the system SHALL resolve them to exactly one outcome.

#### Scenario: Expiry and buy at the same moment
- **WHEN** a listing expires while a buyer buys it
- **THEN** exactly one of the two succeeds and exactly one mailbox row is created for the item

### Requirement: Expiry is organizational
The system SHALL expire listings only by their configured duration and SHALL NOT show any countdown that pressures a buyer.

#### Scenario: Buyer view
- **WHEN** a buyer views a listing close to its expiry
- **THEN** the GUI shows no countdown or "only N left" message

### Requirement: Sales tax
The system SHALL charge a sales tax of 5 percent of the price, configured as basis points, and SHALL post the tax to `system:market_tax`, an account that no command ever debits, so that the tax leaves circulation.

#### Scenario: Tax is removed from circulation
- **WHEN** a purchase of 100 coins completes
- **THEN** the tax of 5 coins is posted to `system:market_tax` and the sum of player balances drops by 5 coins

### Requirement: Season end closes listings
When a season ends, the system SHALL close every active listing and every pending listing of that season, and the closed listings SHALL leave the market. A closed item SHALL NOT be returned to its seller: it is queued in the mailbox of the ending season, which expires with that season under the item-mailbox rules, so the item is lost.

#### Scenario: Season ends with listings
- **WHEN** a season ends with two active listings and one pending listing
- **THEN** all three leave the market and their items are queued in the mailbox of the ending season, not returned to the sellers

#### Scenario: Season ends before a listing's expiry
- **WHEN** a listing's 48 hour expiry falls after the season end
- **THEN** the listing closes at the season end instead of its expiry and its item is not returned

### Requirement: Market permission
The system SHALL gate `/market` with `skygridng.economy.market`, which is granted to all players by default.

#### Scenario: Permission removed
- **WHEN** a player without `skygridng.economy.market` runs `/market`
- **THEN** the command is refused
