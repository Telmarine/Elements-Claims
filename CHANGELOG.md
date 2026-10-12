# Elements: Claims changelog

## 1.0.0 (in development)

### Phase 1 — core
- **Claim Deed** - a paper with a hidden tag (plain paper does nothing), never used up. Right-click claims the chunk you stand in; sneak + right-click unclaims it; right-click inside your own claim shows its info. The lore shows the counter live ("Chunks claimed: 37 / 300"). `/claim deed` gives a new one if lost; optional Deed on first join (`deed_on_first_join`, off by default).
- **Chunk allowance** - starter value in claims.toml (default 100), raised by the `claims.limit` number node (Nexus: `claims.limit=300`). Rank changes show straight away, offline players included. Unclaiming gives the chunk back.
- **Claims** - a chunk claimed next to your own claim joins it, and claims it connects merge. Optional minimum gap between different players' claims (off by default). Dimensions can be closed to claiming.
- **Ranks** - every claim has Owner, Admin, Member and Visitor with the SPEC defaults (Visitor: walk in and use shops only). Rank names, flags, order and extra ranks are stored now and editable from the menu in Phase 2.
- **Trust** - `/trust`, `/untrust`, `/trustlist`. Works for offline players who have joined before. Trusted players go to the lowest trusted rank.
- **Protection** - break/place, using blocks (containers, doors, redstone, farming blocks, anything clickable), using items on blocks (tools, buckets, fire, boats, wrenches...), mobs (trading, riding, farming, attacking players / pets / animals / monsters, including with arrows), armor stands and item frames, item pickup (your own drops always), ender pearls and chorus fruit, entering (bans, private claims), flying and elytra. Blocks and items from other mods are sorted by what they do, so modded containers and machines are covered. Each rank flag has its own switch in features.toml.
- **Staff** - `claims.bypass` builds and edits in any player claim with no toggle; `claims.admin` does the same in admin claims. `/claims info`, `/claims reload`.
- **Outlines** - glowing borders of nearby claims while holding the Deed, plus a white outline around the unclaimed chunk you stand in. Sent only to that player; colours, radius and block in claims.toml.
- Land flags are saved with every claim (defaults from the SPEC) and take effect in Phase 3.

### Phase 2 — menus
- **Land Config** - right-click with the Deed inside a claim you manage, or `/claim`. Opens Land Flags, Ranks, Player Management, Bans, Visitor settings, Rename and Private. Players only see the buttons they're allowed to use.
- **Land Flags** - switch each land flag for the claim (they take effect in Phase 3).
- **Ranks** - ranks top (Owner) to bottom (Visitor). Left-click to pick a rank up and left-click where it goes; shift + left / right-click moves it up / down; right-click edits its flags. Add, rename and delete ranks (max in claims.toml, default 10). Deleted ranks' players move one rank down.
- **Rank flags** - click to switch each flag. Giving a rank Manage Players asks "Are you sure?". Visitor can't get the management flags.
- **Player Management** - every trusted player's head, highest rank first. Promote, demote, remove (with confirm) and ban.
- **Bans** - banned players (click to unban) and "Ban a Player" by name. Banned players are pushed out and can't enter.
- **Private claims** - only trusted players may enter.
- **Who can do what** - Owner and staff (`claims.bypass`, `claims.admin` in admin claims): everything. Manage Land: rename, land flags, private, and flags of ranks below their own. Manage Players: promote / demote / remove / ban players below their own rank, up to the rank just under theirs. Rank names, order, adding and deleting: owner and staff.
- New switches under `[menu]` in features.toml; `ranks.max`, `ranks.name_max_length`, `claims.name_max_length` in claims.toml.

### Phase 3 — land flags
- All 12 land flags now work: **PvP** (players, their arrows and tamed pets can't hurt other players), **Explosions** (blocks, frames, armor stands, boats, minecarts and items are kept safe), **Mob Griefing** (endermen, ravagers, withers, the dragon...), **Outside Griefing** (pistons, fluids, dispensers and projectiles from outside the border), **Fire Spread**, **Fluid Flow**, **Monster Spawning** / **Animal Spawning** (natural spawns, patrols, raids), **Crop Trampling**, **Growth** (crops and saplings), **Decay & Melt** (leaves, ice, snow) and **Enter/Leave Titles**.
- Each land flag has its own switch under `[land_flags.flags]`; titles can show above the hotbar or as a big title.
- **Personal settings** in the new **My Claims** menu (`/claim` outside a claim): enter/leave messages and trust messages on or off. My Claims also lists your claims and your chunk counter.
- Riding: boats, minecarts, horses and other players need Vehicles & Riding; seats from furniture mods need Interact.

### Phase 4 — admin claims
- **Admin Deed** (`/claim deed admin`, `claims.admin`): right-click claims the chunk as an admin claim (or opens its menu), sneak + right-click unclaims. Admin claims count against nothing and never expire; players can never claim inside them.
- Staff with `claims.admin` build and edit in admin claims automatically — no command or mode switch.
- **Admin Settings** in Land Config: keep inventory, no hunger, block entry, block exit (e.g. a jail), and console commands when a player walks in or out (`{player}` = their name).
- Visitors in new admin claims can use containers (community / ender chests), crates, doors and NPC shops by default — still switchable in the Visitor settings.

### Phase 5 — auto-detect
- **Startup auto-sort**: every registered block, item and mob from every mod is sorted into a flag. Results per mod and your overrides live in `detected.toml` (`[overrides.blocks]`, `[overrides.entities]`; `none` = never protected).
- **Machine detection**: the first time a machine (fake player) from any mod acts in a claim it is recorded. `/claims detected` lists them with click buttons: **Owner** (default — machines inside the claim always run; from outside they act as the player who placed them, when the mod stores one), **Allow** or **Deny**.

### Phase 6 — sub-areas
- **Sub-area Stick** (Land Config → Sub-areas): right-click two corners inside your claim for a block-precise area. Yellow outlines show while holding the stick.
- Each sub-area has its own players (a rank inside that area only), its own Visitor rules, a name, and can be deleted. Rank flag **Sub-areas** lets members manage them.

### Phase 7 — mod links
- **Create**: machines inside a claim always run; stationary drills/saws and contraption drills, saws, ploughs, rollers and harvesters can't work blocks across a claim border (unless both claims have the same owner). Deployers act as their owner.
- **Sophisticated Backpacks/Storage** (pump upgrades), **Crystal Tools** (vein mining, 3x3, tree chopping and area stripping now need Build per block), **Waystones** (no teleporting into claims you can't enter), **Gravestone** (owners can always open their grave), **Lootr** (loot chests open to everyone), **Xyndra's Crates** (crates always open), **Easy NPC** (everyone can talk; editing/moving tools need Manage Land), **Carry On** (blocks need Build + Containers, mobs their Attack flag; shops, crates, Lootr, waystones and Easy NPCs can't be picked up), **Tom's Simple Storage** (connectors can't link containers in someone else's claim), **Spud's Shops** and **Elements: Economy** shops (Shops: Use, including left-click buying), **MineColonies** (no claims over colonies), fishing rods incl. **Aquaculture** (hooking mobs / items).
- Each link has its own switch under `[integrations.mods]` — written only for mods that are installed.
- **Public API** `com.tac5studios.elementsclaims.api.ClaimsAPI`: `can`, `canBuild`, `canOpenContainers`, `canUseShops`, `canCreateShops`, `claimName`, `owner`, `isTrusted`...

### Phase 8 — lag and entity limits
- **Lag limits** (refuse new placements only): armor stands, item frames, minecarts, boats, paintings, hoppers, observers, pistons, comparators, repeaters, droppers/dispensers, redstone lamps, furnaces, beacons, spawners — each with its own switch in features.toml and a number in `limits.toml`, per chunk or per claim. Add any block/entity/tag under `[custom]`.
- **Per-mod limits**: `config/elements_claims/mods/create.toml` and `toms_storage.toml`, created only when that mod is installed, each with its own switch.
- **Entity limits** (off by default): pets, passive mobs, monsters, villagers per claim and/or per chunk. Over a limit, breeding, spawn eggs and spawners stop adding and the land owner is told. Easy NPCs never count.

### Phase 9 — expiry, selling, renting
- **Abandoned claims** (off by default, `expiry.toml`): owner offline X days → marked abandoned (still protected) → after the grace period freed, or put up for sale when selling is on. Logging in clears it. Exempt players list and `claims.expiry.exempt`.
- **Selling** (off by default, needs Elements: Economy): owners set a price for a claim (Land Config) or a sub-area. Buyers right-click with their Deed inside and confirm; money goes to the owner and ownership transfers (a sub-area buyer gets its rank there for good).
- **Renting sub-areas** (off by default): each owner chooses whether to rent, the price, period (`market.toml`), renter rank and whether leftover items go back to the renter. Paid in advance, renewable; renters are removed when time runs out. Items owed to offline players are handed over on their next login.

### Phase 10 — importers and maps
- `/claims import <opac|ftb|flan|cadmus>` shows a preview; `... confirm` imports (a backup of claims.json is made first). Reads the other mod's files straight from the world, so it doesn't need to be installed. Server claims become admin claims, parties/teams/trusted players become members. Chunks already claimed are skipped.
- **BlueMap** and **squaremap**: claims drawn on the map with name and owner (refreshed 30 s after changes).

### Not included
- JourneyMap overlays need JourneyMap's API library at build time; Xaero's maps can't show other mods' claims without a client mod; Dynmap has no NeoForge 1.21.1 build.
- Nexus side panel / waystone rules / `/sethome` in claims, Elements: Armor Stands and Elements: Economy shop creation need small changes in those mods to call the Claims API.
