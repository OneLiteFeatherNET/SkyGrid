# Design specs

Design statements for the backlog epics that are specified but not implemented yet. Each spec
contains the full user stories with EARS acceptance criteria and MoSCoW tags, the design, the test
plan, and the decisions of the product owner. The condensed stories live in
[`docs/USERSTORIES.md`](../USERSTORIES.md).

- [Seasons](seasons.md) (epic I3): fixed-length seasons, each with its own world and challenge set,
  switched on schedule into a fresh SkyGrid world, with per-season progress archives.
- [Prestige](prestige.md) (epic I4): opt-in prestige steps per challenge, with admin-defined bounds
  and no reward coupling to behavior signals.
- [Economy](economy.md) (epic J1): a season-scoped coin ledger in PostgreSQL, pay, an admin shop, a
  player market with item escrow, a mailbox, and a Vault provider. Coins are never sold for real money.

## Delivery plan

The three specs ship in one order: seasons first, then prestige, then economy. Each entry is one pull
request, squash-merged, and its title is the Conventional Commit that lands on `main`. Each PR builds
and tests green on its own. The Flyway fix (one Flyway location and one history table per adapter,
branch `origin/fix/flyway-migration-clash`) is merged before the first persistence slice.

1. `docs(userstories): specify seasons, prestige and economy` (this change: user stories, design specs,
   index)

Seasons (I3):

2. `feat(api): add season domain model` (includes the `ProgressScope` record)
3. `feat(config): load season definitions from seasons directory`
4. `feat(persistence): scope challenge tiers and task completions by season` (the one migration of the
   task completion table: season id and prestige step, primary key on `ProgressScope` plus task)
5. `feat(persistence): add season run repository`
6. `feat(core): add season timeline and transition state machine`
7. `feat(platforms): rotate season worlds and move players`
8. `feat(command): add season admin and player commands`
9. `docs(admin): document season operation and cloudnet static service`

Prestige (I4):

10. `feat(api): add prestige ladder and difficulty modifier model`
11. `feat(config): load prestige ladders from challenge files`
12. `feat(persistence): add player prestige repository` (season-scoped rows; the task completion step
    is already in place from slice 4)
13. `feat(core): add prestige use case`
14. `feat(command): add skygrid prestige commands`
15. `feat(challenge): apply prestige modifiers in challenge behaviors`
16. `feat(scoreboard): show prestige step in the challenge sidebar`

Economy (J1):

17. `feat(economy): add money model, wallet ports and wallet service`
18. `feat(economy): add postgres ledger adapter`
19. `feat(economy): add balance, pay and audited admin commands`
20. `feat(challenge): add currency task reward`
21. `feat(economy): register as vault economy provider`
22. `feat(economy): add mailbox and inventory journal`
23. `feat(shop): add configurable admin shop gui`
24. `feat(market): add player marketplace with item escrow`
25. `feat(economy): add ledger audit command`

Within the seasons block, slices 2, 3 and 5 are independent and can run in parallel, slice 4 needs
slice 2, slice 6 needs slices 2 to 5, and slices 7 and 8 need slice 6. The prestige block starts after
slice 9, because it relies on the season ids and the `ProgressScope` key. In the economy block, slices
17 to 19 are the minimum shippable economy. Slices 20 and 21 are independent of each other after slice
19, and slice 22 precedes slices 23 and 24.
