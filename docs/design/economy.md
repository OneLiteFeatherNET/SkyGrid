# Epic J1: SkyBlock-style player economy (requirements and design spec)

Status: specified, not implemented. Reference state: `origin/main` (3ad20f4). Prerequisite: the Flyway
fix (one location and one history table per adapter, branch `origin/fix/flyway-migration-clash`)
is merged first. Before implementation this spec passes the I5 gate (`player-psychology-designer`).

Product-owner decisions already taken: an own currency in PostgreSQL with a `Money` record in
`api.model` (no JSR 354); SkyGrid registers itself as a Vault economy provider; v1 scope is
(a) balance, `/pay` and audited admin commands, (b) a currency task reward, (c) a configurable admin
shop with a GUI, (d) a player marketplace with item escrow.

## 1. User stories

Roles as in USERSTORIES.md, plus **player** (as used in I2/I2a) and **plugin developer** (a third
party plugin consuming Vault). EARS keywords: *When* (event), *While* (state), *If ... then*
(unwanted behavior), *Where* (optional feature), no keyword (ubiquitous).

### J1a: Balance [Must]
As a **player**, I want to see my coin balance, so that I know what I can afford.
- The system shall store each balance as a non-negative integer count of minor units (`long`).
- When a player joins for the first time, the system shall create their account with the configured
  starting balance, recorded as one ledger transaction with key `start:<seasonId>:<uuid>`.
- When a player runs `/balance` (alias `/bal`), the system shall show their balance formatted with
  the configured currency name and fractional digits.
- When a player holding `skygridng.economy.balance.others` runs `/balance <player>`, the system
  shall show that player's balance.
- If the database is unreachable, then the system shall reply with a localized "economy
  unavailable" message and shall not show a cached value as current.

### J1b: Pay another player [Must]
As a **player**, I want to send coins to another player, so that we can trade outside the market.
- When a player runs `/pay <player> <amount>`, the system shall debit the payer and credit the
  recipient in one database transaction, or change neither.
- If the payer's balance is lower than the amount, then the system shall reject the payment and
  echo the amount and the current balance (F1 style).
- If the amount is zero, negative, not representable in the configured fractional digits, or above
  `economy.pay.maxAmount`, then the system shall reject it and echo the invalid value.
- If the recipient is the payer or has never joined, then the system shall reject the payment.
- If the recipient's balance would exceed `economy.maxBalance`, then the system shall reject the
  payment without changing either balance.
- While the payer's pay cooldown (`economy.pay.cooldown`, default 2 s) is running, the system shall
  reject a further `/pay` with the remaining time.
- When a payment succeeds and the recipient is online, the system shall notify the recipient in chat.

### J1c: Audited admin adjustments [Must]
As a **server admin**, I want to give, take and set balances with a full audit trail, so that I can
fix mistakes and reward events without losing track of where money came from.
- When an admin holding `skygridng.economy.admin` runs `/skygrid eco give|take <player> <amount>
  [reason]`, the system shall post one ledger transaction against the `system:mint` account that
  records the actor (admin UUID or `console`) and the reason.
- If `take` exceeds the balance, then the system shall reject it and echo the balance (no clamping).
- When an admin runs `/skygrid eco set <player> <amount>`, the system shall require
  `/skygrid eco set <player> <amount> confirm` (F2 convention) and post the difference as a delta.
- When an admin runs `/skygrid eco history <player> [page]`, the system shall list that player's
  ledger entries newest first (time, reason, delta, balance after, counterparty, actor).
- The system shall never update or delete a ledger row; corrections are new, opposite transactions.

### J1d: Currency task reward [Must]
As a **server admin**, I want a task to reward coins, so that challenges feed the economy.
- The system shall accept `type: currency` with `amount: "<decimal>"` as a task reward in
  `challenges/<id>.<ext>` and in the generated challenge schema.
- When `TaskProgressUseCase#completeTask` records a new completion whose reward is a currency
  reward, the system shall credit the player with idempotency key
  `task:<seasonId>:<challengeId>:<taskId>:<playerId>`, also while the player is offline.
- If the credit for an already recorded completion is missing (crash between the two commits), then
  the system shall apply it on the player's next join; a key that already exists is a no-op.

### J1e: Vault economy provider [Must]
As a **plugin developer**, I want SkyGrid's currency behind the Vault API, so that existing shop,
rank and placeholder plugins work with it unchanged.
- Where Vault or VaultUnlocked is installed, the system shall register a provider for the legacy
  `net.milkbowl.vault.economy.Economy` and the VaultUnlocked `net.milkbowl.vault2.economy.Economy`
  service at the configured `ServicePriority`.
- Where neither is installed, the system shall start normally, log one info line, and keep every
  other economy feature working.
- When a Vault call passes an amount that is negative, NaN, infinite or not exactly representable in
  the configured fractional digits, the system shall return a failure response and change nothing.
- Every successful Vault deposit or withdrawal shall be one ledger transaction tagged with the
  calling plugin's name where the API provides it.

### J1f: Admin shop [Must]
As a **player**, I want to buy and sell items at fixed prices in a shop GUI, so that I can turn
SkyGrid loot into coins and coins into blocks I lack.
- The system shall load shops from `shops/<id>.<ext>` (JSON/YAML/TOML, one file per shop) with a
  generated JSON schema in `schema/`.
- When a player opens `/shop [id]`, the system shall show a chest GUI with each entry's item,
  quantity, buy price and sell price, and the player's balance.
- When a player clicks buy, the system shall debit the price and deliver the item through the
  mailbox (J1h) in one transaction.
- When a player clicks sell, the system shall remove exactly the configured quantity of pristine
  matching items and credit the sell price, crash safe (section 2.7).
- If a shop file has an entry whose unit sell price exceeds the lowest unit buy price of the same
  item in any shop, then the system shall reject that file on load with an error naming both
  entries (no arbitrage loop).
- While a player is in creative or spectator mode, the system shall refuse shop sells.

### J1g: Player marketplace [Must]
As a **player**, I want to list items for a price and buy other players' listings, so that a player
driven market forms.
- When a player runs `/market sell <price>`, the system shall move the held stack into escrow and
  create an active listing with the configured duration.
- When a player buys a listing in `/market` (GUI), the system shall in one transaction: verify the
  listing is still active, debit the buyer, credit the seller minus the sales tax, credit the tax to
  `system:market_tax`, mark the listing sold, and queue the item for the buyer's mailbox.
- If two players buy the same listing concurrently, then exactly one purchase shall succeed and the
  other shall get a "already sold" message with no balance change.
- When a seller cancels an active listing, or when a listing expires, the system shall queue the
  item to the seller's mailbox and the listing shall leave the market.
- If the player already has `economy.market.maxListings` active listings, the price is outside
  `[minPrice, maxPrice]`, or the serialized item exceeds `economy.market.maxItemBytes`, then the
  system shall reject the listing and keep the item in hand.
- The system shall not let a player buy their own listing.
- The GUI shall show price, tax and the seller's net before the seller confirms a listing.

### J1h: Mailbox for undelivered items [Must]
As a **player**, I want items I bought, or listings that came back, to wait for me, so that nothing
is lost when I am offline or my inventory is full.
- When a delivery is queued for an online player with enough inventory space, the system shall
  deliver it immediately.
- If the inventory has no room for the whole stack, then the system shall keep it in the mailbox and
  tell the player to run `/market claim`.
- When a player joins, the system shall reconcile pending deliveries and pending escrows (2.7) before
  delivering anything new.

### J1i: Ledger integrity check [Should]
As a **server owner**, I want to verify that no money was created or lost, so that I can trust the
economy after a crash.
- When an admin runs `/skygrid eco audit`, the system shall report whether every transaction's
  postings sum to zero and whether every account balance equals the sum of its postings, naming
  each mismatch.

### J1j: Market browsing [Should]
- The market GUI shall page listings newest first and filter by item id (`/market search <item>`).
- Sorting by unit price [Could].

### J1k: Balance leaderboard [Won't, this release]
Deferred pending the I5 review of social comparison (section 3.2, item 3).

## 2. Design

### 2.1 Module placement

| Module | New content |
|---|---|
| `api` | `api.model`: `Money`, `AccountId`, `LedgerEntry`, `TransferReason`, `TransferResult`, `ItemPayload`, `Listing`, `ListingState`, `Delivery`, `ShopCatalog`, `ShopEntry`, `RewardKey`, `EconomySettings`; `Reward.CurrencyGrant` |
| `ports` | `ports.in`: `WalletUseCase`, `ShopUseCase`, `MarketUseCase`, `MailboxUseCase`; `ports.out`: `LedgerRepository`, `MarketRepository`, `MailboxRepository`, `ShopRepository` |
| `core` | `core.service`: `WalletService`, `ShopService`, `MarketService`, `MailboxService`, `CurrencyRewardReconciler`, `CooldownTracker` (all on an injected `java.time.Clock`) |
| `adapters/economy` (new) | `adapters.economy`: Postgres repositories on `AbstractJpaRepository`, persistence unit `economy`, Flyway `classpath:db/migration/economy`, history table `flyway_history_economy` |
| `adapters/config` | `ShopDtos`, `JacksonShopRepository`, `shops/` defaults, shop schema, `economy` section in `ConfigDtos`, `currency` reward in `ChallengeDtos.RewardDto` |
| `adapters/command` | `EconomyCommands`, `ShopCommands`, `MarketCommands` handlers, `Messages` additions |
| `platforms/common` | `platforms.common.economy`: executor, GUIs, item serializer, inventory journal, join reconciler; `platforms.common.economy.vault`: the two Vault providers; Guice `EconomyModule`; i18n |
| `platforms/paper` | `softDepend`, permissions, `compileOnly` Vault APIs stay in `platforms/common` |

One adapter module owns wallet, market and mailbox tables on purpose: a purchase must change the
listing, three balances and the mailbox in a single Postgres transaction, which needs a single
persistence unit (each `AbstractJpaRepository` has its own `EntityManagerFactory`).

### 2.2 Domain model (`api.model`, records validated in compact constructors)

- `Money(long minor)`: `minor >= 0`; `plus`/`minus` via `Math.addExact`/`subtractExact` (throws
  on overflow or negative result); `times(int)` via `multiplyExact`. No `double` anywhere in core.
- `EconomySettings(int fractionalDigits /*0..4*/, String currencySingular, String currencyPlural,
  Money startingBalance, Money maxBalance, PaySettings pay, MarketSettings market)`;
  `Money parse(String decimal)` lives here: `new BigDecimal(s).movePointRight(fd).longValueExact()`,
  rejecting a fractional remainder instead of rounding.
- `AccountId` sealed: `Player(UUID id)`, `System(String name)` (`mint`, `shop`, `market_tax`).
  System accounts may go negative (`mint` is the source of all money), so the invariant "sum of all
  balances = 0" holds at all times and is checkable.
- `TransferReason` enum: `STARTING_BALANCE, PAY, ADMIN_GIVE, ADMIN_TAKE, ADMIN_SET, TASK_REWARD,
  SHOP_BUY, SHOP_SELL, MARKET_SALE, VAULT_DEPOSIT, VAULT_WITHDRAW, SEASON_RESET`.
- `Posting(String idempotencyKey, TransferReason reason, String actor, String note,
  List<Leg> legs)` with `Leg(AccountId account, long delta)`; constructor asserts legs sum to zero,
  no zero deltas, no duplicate account.
- `TransferResult` sealed: `Applied(UUID txId, Map<AccountId, Money> balancesAfter)`,
  `AlreadyApplied(UUID txId)`, `InsufficientFunds(AccountId, Money balance)`,
  `BalanceCapExceeded(AccountId)`, `UnknownAccount(AccountId)`.
- `ItemPayload(ItemId item, int amount, byte[] data)`: opaque platform bytes (Paper
  `ItemStack#serializeAsBytes`), defensive copies in and out; `item`/`amount` exist for display and
  search only.
- `Listing(UUID id, UUID seller, ItemPayload item, Money price, Instant created, Instant expires,
  ListingState state)`; `ListingState { PENDING, ACTIVE, SOLD, CANCELLED, EXPIRED }`.
- `Delivery(UUID id, UUID player, ItemPayload item, String source, UUID sourceRef)`.
- `ShopEntry(ItemId item, int quantity, Optional<Money> buy, Optional<Money> sell, int slot)`,
  `ShopCatalog(String id, String title, int rows, List<ShopEntry>)` with the arbitrage check across
  catalogs done in `ShopService` at load.
- `Reward.CurrencyGrant(Money amount)` (`amount.minor() > 0`), plus `RewardKey(String challengeId,
  String taskId)`.

### 2.3 Ports and core services

Ports are synchronous and blocking; threading is a platform concern (2.6). Every wallet, market and
mailbox port takes the active `SeasonId` from `CurrentSeason` (see `docs/design/seasons.md`), so all
rows are season-scoped. Idempotency keys carry the season id because they are unique across seasons.

- `ports.out.LedgerRepository`: `TransferResult post(Posting)` (atomic, idempotent by key, enforces
  `balance >= 0` for player accounts and `<= maxBalance`), `Optional<Money> balance(AccountId)`,
  `boolean ensureAccount(UUID, Posting startingBalance)`, `List<LedgerEntry> history(AccountId,
  int page, int size)`, `AuditReport audit()`.
- `ports.out.MarketRepository`: `createPending(Listing)`, `activate(UUID)`, `discardPending(UUID)`,
  `PurchaseResult purchase(UUID listingId, UUID buyer, Money tax, Instant now)` (listing, postings
  and mailbox row in one transaction), `cancel(UUID listingId, UUID seller)`,
  `int expireDue(Instant now, int batch)`, `page(Filter, int page)`, `countActive(UUID seller)`,
  `List<Listing> pendingFor(UUID seller)`.
- `ports.out.MailboxRepository`: `pendingFor(UUID)`, `markDelivered(UUID)`, plus shop-sale intents
  `createSaleIntent`, `commitSaleIntent(UUID, Posting)`, `abortSaleIntent(UUID)`, `pendingSaleIntents(UUID)`.
- `ports.out.ShopRepository`: `List<ShopCatalog> load()`.
- `ports.out.RewardGrantor#grant(UUID playerId, Reward reward, RewardKey key)`: the key is added so
  the currency branch can build its idempotency key; `TaskProgressService` passes it.
- `WalletService`: parses and validates amounts, applies `CooldownTracker` (Clock based, per-player
  map, evicted on quit), builds postings (pay: payer `-a`, recipient `+a`; give: mint `-a`, player
  `+a`), generates the idempotency key once per command invocation (`pay:<uuid4>`) so a retry after
  a deadlock never applies twice.
- `ShopService`, `MarketService` (tax = `price * taxBasisPoints / 10_000`, integer division
  rounds the tax down, computed with `multiplyExact`), `MailboxService`.
- `CurrencyRewardReconciler`: on join, for every configured task with a `CurrencyGrant` that
  `TaskProgressUseCase#isCompleted` reports done, re-posts with the same key; `AlreadyApplied` is the
  normal case. Changing a reward amount later never tops up already-paid completions (documented).

### 2.4 PostgreSQL schema (`adapters/economy`, `V1__create_economy.sql`)

```sql
CREATE TABLE economy_account (
    season_id  TEXT NOT NULL CHECK (char_length(season_id) <= 64),
    account_id TEXT NOT NULL CHECK (char_length(account_id) <= 64), -- 'player:<uuid>' | 'system:<name>'
    kind       TEXT NOT NULL CHECK (kind IN ('PLAYER', 'SYSTEM')),
    balance    BIGINT NOT NULL,
    PRIMARY KEY (season_id, account_id),
    CHECK (kind = 'SYSTEM' OR balance >= 0)
);
CREATE TABLE economy_transaction (
    tx_id           UUID PRIMARY KEY,
    season_id       TEXT NOT NULL CHECK (char_length(season_id) <= 64),
    idempotency_key TEXT NOT NULL UNIQUE CHECK (char_length(idempotency_key) <= 160),
    reason          TEXT NOT NULL,
    actor           TEXT NOT NULL CHECK (char_length(actor) <= 64), -- uuid, 'console', 'plugin:<name>'
    note            TEXT CHECK (char_length(note) <= 256),
    created_at      TIMESTAMPTZ NOT NULL
);
CREATE TABLE economy_posting (
    tx_id         UUID NOT NULL REFERENCES economy_transaction,
    season_id     TEXT NOT NULL,
    account_id    TEXT NOT NULL,
    delta         BIGINT NOT NULL CHECK (delta <> 0),
    balance_after BIGINT NOT NULL,
    PRIMARY KEY (tx_id, account_id),
    FOREIGN KEY (season_id, account_id) REFERENCES economy_account (season_id, account_id)
);
CREATE INDEX economy_posting_account ON economy_posting (season_id, account_id, tx_id);
CREATE TABLE market_listing (
    listing_id UUID PRIMARY KEY,
    season_id  TEXT NOT NULL CHECK (char_length(season_id) <= 64),
    seller_id  UUID NOT NULL,
    item_id    TEXT NOT NULL CHECK (char_length(item_id) <= 128),
    amount     INT NOT NULL CHECK (amount > 0),
    item_data  BYTEA NOT NULL,
    price      BIGINT NOT NULL CHECK (price > 0),
    state      TEXT NOT NULL CHECK (state IN ('PENDING','ACTIVE','SOLD','CANCELLED','EXPIRED')),
    buyer_id   UUID,
    created_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    closed_at  TIMESTAMPTZ
);
CREATE INDEX market_listing_active ON market_listing (season_id, state, expires_at);
CREATE INDEX market_listing_seller ON market_listing (season_id, seller_id, state);
CREATE TABLE mailbox_delivery (
    delivery_id  UUID PRIMARY KEY,
    season_id    TEXT NOT NULL CHECK (char_length(season_id) <= 64),
    player_id    UUID NOT NULL,
    item_id      TEXT NOT NULL, amount INT NOT NULL CHECK (amount > 0), item_data BYTEA NOT NULL,
    source       TEXT NOT NULL, source_ref UUID,
    delivered_at TIMESTAMPTZ,
    created_at   TIMESTAMPTZ NOT NULL
);
CREATE INDEX mailbox_pending ON mailbox_delivery (season_id, player_id) WHERE delivered_at IS NULL;
CREATE TABLE shop_sale_intent (
    intent_id UUID PRIMARY KEY, season_id TEXT NOT NULL, player_id UUID NOT NULL, shop_id TEXT NOT NULL,
    item_id TEXT NOT NULL, amount INT NOT NULL, payout BIGINT NOT NULL CHECK (payout > 0),
    state TEXT NOT NULL CHECK (state IN ('PENDING','COMMITTED','ABORTED')),
    created_at TIMESTAMPTZ NOT NULL
);
```

System accounts (`mint`, `shop`, `market_tax`) are created per season when the season is prepared.
`economy_transaction`/`economy_posting` are
append-only (the repository has no update/delete path; optionally a `REVOKE UPDATE, DELETE` for the
app role in the operator runbook). Timestamps come from the injected `Clock`, never `now()`, so tests
are repeatable. Data minimization matches I2a: UUIDs only, no names stored.

`post(Posting)` algorithm (READ COMMITTED, one transaction):
1. `INSERT INTO economy_transaction ... ON CONFLICT (idempotency_key) DO NOTHING`; zero rows means
   `AlreadyApplied` (look up `tx_id`, return).
2. `SELECT account_id, balance FROM economy_account WHERE account_id IN (...) ORDER BY account_id
   FOR UPDATE` (fixed lock order prevents A->B / B->A deadlocks); missing row means `UnknownAccount`.
3. Compute new balances in Java with `addExact`; on a negative player balance or one above
   `maxBalance`, roll back and return the typed failure.
4. `UPDATE` each account, `INSERT` postings with `balance_after`, commit.
The adapter retries the whole transaction up to 3 times on SQLState `40P01` (deadlock) or `40001`;
safe because the key makes it idempotent.

### 2.5 Configuration

`config.<ext>` gains an `economy` section (currency names, `fractionalDigits` default 0,
`startingBalance`, `maxBalance` default `1000000000000`, `pay.cooldown`, `pay.maxAmount`,
`market.taxBasisPoints`, `market.duration`, `market.maxListings`, `market.minPrice`,
`market.maxPrice`, `market.maxItemBytes` default 32768, `vault.enabled`, `vault.priority`). Amounts are
decimal strings (`"1.50"`), parsed by `EconomySettings.parse`, so YAML/TOML float rounding never
touches money.

New folder `shops/`, one file per shop, sibling of `presets/`, `rules/`, `challenges/`, bundled
default `shops/general.yml`, schema `schema/shop.schema.json` generated by `ConfigSchemas` (victools,
Draft 2020-12) from `ShopDtos`:

```yaml
id: general
title: "<gold>General Store"
rows: 6
entries:
  - item: minecraft:oak_sapling
    quantity: 1
    buy: "50"
    sell: "10"
    slot: 10
  - item: minecraft:cobblestone
    quantity: 64
    sell: "16"          # sell-only entry, no buy key
    slot: 11
```

Items are namespaced `ItemId`s (platform-neutral rule), resolved to `Material` only in the platform
GUI; an unknown id disables that entry with a warning instead of the `RewardGrantorImpl` barrier
fallback (a barrier would be a sellable placeholder). Challenge reward DTO adds `type: currency`,
`amount: "250"` to `RewardDto` and the challenge schema.

### 2.6 Platform wiring and threading

- **Threading rule.** No economy port call runs on the server main thread, except the Vault read
  path (below). `platforms.common.economy.EconomyExecutor` is a bounded `ThreadPoolExecutor`
  (default 4 threads, matching Hikari's pool) created in `EconomyModule` and shut down in
  `onDisable` with a bounded `awaitTermination`. Command handlers return `CompletableFuture<Void>`
  (Cloud annotations treat these as async) built on the `java.util.concurrent.Executor` passed to
  their constructor, so `adapters/command` stays framework free. Inventory and GUI changes hop back
  with `server.getScheduler().runTask`. Note: today `TaskProgressService`/`ChallengeProgressService`
  run JDBC on the main thread; the currency reward branch must not add to that, so
  `RewardGrantorImpl` hands `CurrencyGrant` to the executor.
- **Per-player in-flight guard.** A `Set<UUID>` of players with an economy operation in flight; GUI
  clicks and commands from a player already in it are dropped with an action-bar hint. This kills
  double-click and macro double-submit at the source; the database invariants still hold without it.
- **Vault binding.** `compileOnly("net.milkbowl.vault:VaultAPI:1.7.1")` (legacy, MilkBowl, last
  release 2020, still what most shop plugins call) and
  `compileOnly("net.milkbowl.vault:VaultUnlockedAPI:2.16")` (repo
  `https://repo.codemc.io/repository/creatorfromhell/`; VaultUnlocked 2.20.x lists support up to MC
  26.2, verify the newest API version on CI). plugin-yml: `softDepend = listOf("Vault",
  "VaultUnlocked")` (VaultUnlocked's own docs still check for plugin name `Vault`; verify at
  implementation). `EconomyFeature#enable` checks `Class.forName("net.milkbowl.vault.economy.Economy")`
  and only then instantiates `platforms.common.economy.vault.VaultRegistrar`, the only class that
  references Vault types, so a server without Vault never loads them (no `NoClassDefFoundError`).
  It registers `LegacyVaultEconomy` and, if the vault2 class exists, `VaultUnlockedEconomy` via
  `ServicesManager#register(..., plugin, priority)` and unregisters on disable. Both adapt to
  `WalletUseCase`: `double`/`BigDecimal` to `Money` via exact conversion (reject, never round),
  `fractionalDigits()` from config, bank and multi-currency methods report unsupported, account
  creation maps to `ensureAccount`.
- **Vault is synchronous by contract** and called on the main thread by other plugins. Reads
  (`getBalance`, `has`) are served from a `BalanceCache` (a plain `ConcurrentHashMap`, refreshed
  after every committed posting on this node and loaded on join, 5 s TTL) because placeholder plugins
  poll every tick. Writes (`withdraw`/`deposit`) go to Postgres synchronously on the calling thread
  (primary-key updates, sub-millisecond on a local database), with a warning log when one exceeds
  50 ms. Correctness never depends on the cache: the write path re-checks the balance under the row
  lock.
- **GUIs.** Plain Bukkit chest inventories with a custom `InventoryHolder` per view
  (`ShopView`, `MarketView`, `MailboxView`, `ConfirmView`). Every `InventoryClickEvent` and
  `InventoryDragEvent` whose top or bottom inventory belongs to such a holder is cancelled first,
  then interpreted by slot; display items are fresh copies tagged with a PDC key
  `skygridng:gui_display` and never leave the view. Opening a GUI from a Bukkit-free handler goes
  through an out port `ports.out.EconomyViews` (`openShop(UUID, String)`, `openMarket(UUID, int)`,
  `openMailbox(UUID)`) implemented in `platforms/common`.
- **Commands** (Incendo Cloud handlers in `adapters.command.handler`): `balance|bal [player]`,
  `pay <player> <amount>`, `shop [id]`, `market [page]`, `market sell <price>`, `market search
  <item>`, `market cancel` (GUI), `market claim`, `skygrid eco give|take|set|history|audit`. Root
  labels are bound in `CommandModule`; see decision 2 in section 6.
- **Permissions** (plugin.yml): `skygridng.economy.balance`, `.pay`, `.shop`, `.market` (default
  true), `skygridng.economy.balance.others` (op), `skygridng.economy.admin` (op, give/take/set/history/
  audit). Every handler also declares its permission so tab completion is gated (F1).
- **i18n.** Keys `skygridng.economy.*` (balance, pay, admin, errors), `skygridng.shop.*`,
  `skygridng.market.*`, `skygridng.mailbox.*` in `Bundle_en.properties`; German via Crowdin
  (`Bundle_de.properties` not hand-edited). Amounts are formatted by a platform `MoneyFormatter`
  (locale-aware grouping, currency singular/plural) and passed as `<arg:N>`. Multi-line output
  (history, audit) is one `sendMessage` per line.

### 2.7 Consistency between database and inventory

Two stores with no shared transaction: Postgres (durable on commit) and the player's inventory
(durable only when the player data file is written; a crash rolls it back to the last save). Every
item movement therefore uses a journal that lives *inside the player data file*: a PDC list
`skygridng:economy_journal` of operation UUIDs on the `Player`. Inventory change and journal entry
happen in the same main-thread tick, so any save contains both or neither; after the change the
platform calls `Player#saveData()` to make it durable immediately.

| Operation | Step 1 (async, DB) | Step 2 (main thread) | Step 3 (async, DB) |
|---|---|---|---|
| List item | insert listing `PENDING` (invisible to buyers) | verify the held stack still equals the snapshot, remove it, add listing id to journal, `saveData` | `ACTIVE`, then main removes the journal id |
| Sell to shop | insert sale intent `PENDING` | verify and remove items, add intent id, `saveData` | post credit with key `shopsale:<intent>` and mark `COMMITTED` in one transaction |
| Deliver (buy, cancel, expire, shop buy) | delivery row already committed with the money transaction | if whole stack fits: add item, add delivery id, `saveData` | `delivered_at`, then drop journal id |

Reconciliation runs on join (and on enable for online players), on the main thread for the journal
read, async for the DB part, before any new delivery:
- `PENDING` listing or sale intent whose id **is** in the journal: the item left the inventory
  durably, so finish step 3. Id **not** in the journal: the item is still (or again) in the
  inventory, so discard the row.
- Undelivered delivery whose id is in the journal: mark delivered. Not in the journal: deliver again.
- Journal ids with no matching open row are dropped (bounded list, max 64 entries).

If step 2 verification fails (item moved, player died or logged out), the row is discarded and
nothing changed. Purchases, cancels and expiries never touch an inventory inside their transaction;
they only create mailbox rows, so they have no crash window at all. Expiry is an async repeating task
(`expireDue` with `FOR UPDATE SKIP LOCKED`, batch 100, every 60 s). Cancel and purchase both lock the
listing row and require `state = 'ACTIVE' AND expires_at > now`, so cancel-versus-buy and
expire-versus-buy races resolve to exactly one winner.

## 3. Abuse and security review

### 3.1 Threats and mitigations

| Threat | Mitigation |
|---|---|
| Money created by concurrent transfers (lost update) | row lock in fixed order plus balance recomputed under the lock; Testcontainers race test |
| Double reward / double admin give on retry | unique `idempotency_key`; retries reuse the key |
| Double purchase of one listing | listing row `FOR UPDATE`, state check, single winner |
| Item dupe via crash between DB and inventory | PDC journal plus `saveData`, join reconciliation (2.7) |
| GUI dupes (shift-click, number keys, double-click collect, drag, cursor on close) | cancel every click/drag on our holders first, display copies only, in-flight guard |
| Selling creative-spawned items | sell and list refused in creative/spectator |
| Shop arbitrage (buy < sell) | load-time check across all shops, file rejected |
| Selling damaged/renamed/enchanted items at full price | shop sell accepts only stacks similar to a fresh `ItemStack(type)` |
| Overflow | `long` minor units, `*Exact` arithmetic, `maxBalance`, `maxPrice`, DB `CHECK` |
| Floating point drift (Vault doubles) | exact conversion or failure response; no rounding |
| Oversized item payloads (book/shulker NBT bombs) | `maxItemBytes` cap on listing, BYTEA never deserialized off-thread |
| Negative or zero amounts | `Money` invariant, parser, DB `CHECK (delta <> 0)` |
| Pay spam / chat harassment | per-player cooldown, `maxAmount`, recipient notification only on success |
| Market spam | `maxListings`, min/max price |
| Admin abuse | every admin action is a ledger row with actor and reason; ledger append-only; `set` needs confirm |
| Third-party plugin minting via Vault | out of our control by design; every Vault write is ledgered with plugin name, visible in `history`/`audit` |
| SQL injection | JPA parameter binding only (existing `AbstractJpaRepository` rule) |
| Main-thread stalls / lag-induced timeouts | async executor; Vault writes logged when slow |

### 3.2 I5 dark-pattern review (challenge-season-system checklist)

Framing: an economy lets players convert play (tasks, loot) into a currency and trade it, to add a
persistent goods layer. Answers below are inputs for `player-psychology-designer`, not a verdict.
1. **Drives**: accomplishment (earning), ownership (balance, items), social influence (market).
   No unpredictability (no random currency drops, crates or gambling sinks) and no loss/avoidance
   (no balance decay, no upkeep). Keep it that way: any future lottery or crate is a new I5 review.
2. **Transparency**: all prices fixed and shown; listing GUI shows price, tax and net before confirm;
   purchase confirm shows balance after. No hidden fees; tax rate visible in `/market` header.
3. **Bartle**: achievers (earn, tasks), socializers (trade, pay), explorers (shop catalogue as a
   guide to what the grid holds). Killers are not served by the economy; challenges still serve
   them. A leaderboard would add competitive comparison, deferred (J1k) because it rewards hoarding.
4. **Adaptive rewards**: none. Currency rewards and shop prices are static config and never react
   to struggle or disengagement signals.
5. **Late joiners**: a persistent balance compounds early-player advantage and market prices inflate
   against newcomers. Mitigation is the season reset policy (section 6, decision 1).
6. **Outside observer**: risk if currency were sold for real money ("pay-to-win coins"). The plugin
   ships no real-money path and the owner has ruled it out (section 6, decision 7). Vault lets
   other plugins do it; that is server policy, documented in the README.
7. **Metrics** for `playtest-qa-coordinator`: balance Gini coefficient per week (failing if above
   0.8), share of active players with at least one trade, listing sell-through rate, median coins
   of players in their first week versus server median, count of `audit` mismatches (must be 0).
8. **Timers**: listing expiry is organizational, owner-configured and loses nothing (item returns
   to the mailbox). No flash sales, no "only N left", no countdown pressure in v1.

## 4. Test plan (F.I.R.S.T.)

All timing through an injected `Clock` (`Clock.fixed`/mutable test clock); no `Thread.sleep`;
concurrency tests use `CountDownLatch`/`CyclicBarrier` and assert on results, not timing.

- **api** (unit): `Money` arithmetic and overflow, `EconomySettings.parse` (rejects `"1.005"` at
  2 digits, `"-1"`, `"1e3"` policy, blanks), `Posting` sum-to-zero guard, `CurrencyGrant` guard,
  `ItemPayload` defensive copy.
- **core** (unit, in-memory `LedgerRepository`/`MarketRepository` fakes in `testFixtures`):
  pay validation branches, cooldown with advancing clock, admin set delta, idempotent key reuse,
  tax math, shop arbitrage detection across catalogs, reconciler re-posts only missing keys,
  `TaskProgressService` passes the right `RewardKey`.
- **adapters/economy** (Testcontainers `postgres:16-alpine`, fresh database or truncated tables per
  test, never shared rows between tests):
  - 200 concurrent transfers in both directions between 10 accounts from 16 threads released by one
    barrier: every call returns, no negative balance, sum of balances unchanged, postings sum to zero,
    `audit()` clean.
  - `SEASON_RESET` of one season: balances reach zero, a second reset of that season posts nothing, and
    `audit()` stays clean.
  - Same idempotency key from 8 threads: exactly one `Applied`.
  - Two buyers on one listing from 2 threads: exactly one `Purchased`, loser's balance unchanged.
  - Cancel versus buy, expire versus buy races: one winner, one mailbox row.
  - `maxBalance` and insufficient funds roll back fully; deadlock retry path (forced with two
    explicit transactions).
  - `PostgresAdapterMigrationTest` (from the Flyway PR) gains the economy adapter: all three adapters
    migrate one database without clashing.
- **adapters/config** (`@TempDir` round trip): shop file in JSON/YAML/TOML, schema generated,
  `currency` reward parsed, decimal string precision kept.
- **adapters/command** (`RecordingCommandSource`, direct handler calls, futures joined with a
  same-thread `Executor`): which `Messages.*` fired per branch, permission declarations.
- **platforms/common** (MockBukkit, fresh `ServerMock` per test, ticks driven explicitly):
  GUI cancels shift-click, hotbar swap, drag, double-click collect; display items carry the PDC tag;
  journal reconciliation table (2.7) row by row with a fake repository; creative refusal; delivery
  with full inventory stays in mailbox; Vault provider exact conversion (`0.1` at 0 digits fails,
  `NaN` fails) against a fake `WalletUseCase`; plugin enables with no Vault class present (bootstrap
  test in `platforms/paper`) and registers both services when a stub Vault API is on the classpath.
- **ArchUnit** (`verification`): new `economyAdapterIsFrameworkFree` (no `org.bukkit..`,
  `io.papermc..`, Guice, jakarta.inject; Hibernate allowed as in the other Postgres adapters);
  new `onlyVaultBindingDependsOnVault` (no class outside `platforms.common.economy.vault..` depends
  on `net.milkbowl..`). `adaptersDependOnPortsAndApiOnlyNotCore` and the layered rule cover the new
  adapter automatically through its `adapters.economy` package; verify the package patterns match.

## 5. Delivery

The delivery plan for seasons, prestige and economy is one ordered list in
[`docs/design/README.md`](README.md#delivery-plan). Slice titles and order are defined there.

## 6. Decisions

The product owner resolved the open questions of this spec as follows.

1. **Season boundary (I3).** Balances are reset per season with one `SEASON_RESET` ledger transaction
   per account, audited, with history kept. All listings are closed into the mailbox. Mailbox items
   expire with the season under the inventory policy of `docs/design/seasons.md` (decision 1), so
   they are wiped with the inventory. Every wallet, market and mailbox row carries the season id.
2. **Root commands.** `/pay`, `/balance`, `/shop` and `/market` are root labels behind the
   `economy.commands.rootLabels` config toggle. The commands are always also reachable as
   `/skygrid eco|shop|market ...` and under the `skygridng:` labels.
3. **Fractional digits.** Coins are whole (`0` fractional digits). The value is fixed for go-live,
   because changing it later rescales every stored row.
4. **Tuning defaults.** Starting balance 0. Market tax 5 %, credited to `system:market_tax` as the main
   money sink. Listing duration 48 h. 10 active listings per player. Pay cooldown 2 s.
5. **Vault priority.** `Normal`, configurable. If another economy plugin already provides Vault, SkyGrid
   logs a warning naming the competing provider.
6. **Servers per database.** One server per database in v1. All invariants live in Postgres, so a
   later multi-server design only needs the balance cache refresh story.
7. **Real money.** Coins are never sold for real money by the server. The README states this when the
   economy ships.
