# Elements: Claims changelog

## 1.0.0 (in development)

### Properties, transfers and no more land market
- Properties are never merged now. If you own more than one, every chunk you claim goes to your active property, wherever it is. A property can have chunks in the Overworld, the Nether and the End. New properties are only started with `/claim create <name>`. Switch the active one with `/claim <property> manage`, or right-click it in your claim list.
- Added `/claim <property> transfer <player>` with a confirm screen. The receiver has to be online and have enough free chunks. They choose to add it to their current property, or keep it as its own property if they have a free slot. Both sides see the chunk count, and only the receiver sees their own free chunks.
- Removed built-in selling and renting, and the market.toml file. Claims doesn't handle money anymore. Elements: Economy only connects through player shops.
- Claims no longer needs Elements: Vault to build.
- Property names have to be different from each other for the same owner, since commands use them.

### Sub-area removals
- Blocks placed in a sub-area are recorded with who placed them. Removing someone whose blocks are still there starts a countdown (`sub_areas.leave_days`, default 7) instead. They keep their sub-area rank to collect their things. When they've taken everything or the time runs out, they're removed and what's left belongs to the owner.

### Backups and alerts
- claims.json gets copied to `backups/` every hour (last 5 kept) and every day (last 7 kept).
- If claims.json can't be read, the newest backup that works is loaded. If none work, saving is paused so nothing gets overwritten.
- A claims file with even one unreadable claim now counts as broken. Before, that claim was skipped and then deleted on the next save.
- detected.toml gets a .bak copy before each rewrite. If it has an error, it's never rewritten and the .bak is used until it's fixed.
- New staff alerts for these problems: a log line, a chat message to staff with `claims.admin` (also when they log in), and an optional Discord webhook with a role ping (`[alerts]` in claims.toml).

### Importers
- All four importers (OPAC, FTB Chunks, Flan, Cadmus) now bring each player's land in as one property with their chunks in every dimension, instead of one claim per world and sub-claim. It becomes their active property if they didn't have one.
- OPAC and Flan sub-claims become sub-areas with their own trusted players. OPAC server sub-claims each become their own admin claim.
- OPAC: "Everyone" access and protection turned off now import as open to everyone instead of nobody. The server's default player config and each sub-claim's own settings are read the same way OPAC reads them. Custom groups match by their exact name, follow groups inside groups, and look up members saved by name only. Protection options now carry over to land flags.

### Machines
- Machines from other mods are judged by who placed them, not by where their stand-in player stands. Create's deployer puts its stand-in 1.5 blocks in front of itself, so a deployer placed outside a claim and aimed in used to count as inside and could grief.
- The land owner's own machines now work even when they sit outside the claim. Machines whose placer can't be found are denied unless staff set them to allow with `/claims detected`.

### Sub-area managers
- The owner can pick one player in each sub-area as its manager. They only need to be in the sub-area, not trusted in the rest of the claim. Managers can add, remove and rank players there with ranks below their own sub-area rank, using the menu or `/trust` and `/untrust`. Only the owner can pick or clear the manager.

### Admin claims
- Items kept by an admin claim's keep inventory setting are now handed back when the player respawns. Before, a player who quit on the death screen and logged back in got them while still dead, and they were lost when they clicked Respawn.
- The README now warns that `claims.admin` holders can add enter/leave commands that run as the console.

### Combat
- Visitors can fight hostile mobs by default now (Attack Monsters is on for the Visitor rank). Attack Animals stays off so farms are safe.
- Sweeping sword hits now check the Attack flags for every mob they hit. Before, only the mob you clicked was checked, so a sweep on a zombie could still hurt the owner's animals or pets.

### Sub-area trust commands
- Added `/trust <player> <subarea> <rank>` and `/untrust <player> <subarea>`. They set a player's rank inside one sub-area only, so someone can be a Member in the claim and have a custom Shop Owner rank just inside their shop.
- Only the claim owner (or staff) can set sub-area ranks, add or remove sub-area players, by command or in the menu.

### Fixes from testing
- Frosted ice from Frost Walker now always melts, even in claims where Decay & Melt is off.
- `/claim deed` has a 30 minute cooldown so it can't be spammed. Change it with `deed.cooldown_minutes` in claims.toml (0 turns it off). Staff with `claims.admin` skip it.
- Sub-area names now get the same cleanup as claim names: trimmed, colour codes stripped and cut to the max length.
- Deleting a rank now also moves sub-area members and the sub-area rent rank to the next rank down. Any old data pointing at a deleted rank gets fixed when the server starts.
- Claim outlines now only draw 2 chunks out from the player (was 3, could be set up to 8), so they're easier on lower-end PCs. `borders.radius` can be 1 or 2, and `borders.max_segments` is capped at 300.
- Claim owners who aren't in the server's name cache used to show up as part of their UUID. Claims now remembers each player's name when they log in (and from OPAC imports) and uses that instead.
- Only the vanilla painting counts toward the painting limit. Hanging items from other mods were being counted as paintings.
- Players who get banned (or locked out by a private claim) while standing inside it now get moved to the nearest safe spot outside it. Before, they kept getting teleported back to a spot inside the claim.
- Every flag (rank flags, land flags, private, sub-area visitor rules) is now set by the claim owner only. Manage Land is down to renaming the claim and seeing its ranks.
- New **Admin Mode** rank flag. Only the owner sees it and turns it on, for the rank they pick as their admins. That rank can trust, untrust, promote, demote, remove and ban players ranked below it. Manage Players on its own only allows promote, demote and remove (no more bans).
- Using a spawn egg on a mob (like making a baby villager) now checks the entity limit first and shows a message, instead of using up the egg and spawning nothing.
- Claim outlines no longer glow. The glow drew through players and blocks, so a player standing lower than you had the line cut across them. Outlines are now full-bright coloured blocks that get hidden normally. Each colour is its own block under `[borders.blocks]` in claims.toml (concrete by default). Web map colours moved to `[maps.colors]`.
- Importing from a claim mod that isn't on the server now logs one line instead of a full error.
- Naming things (claims, ranks, sub-areas, or typing a player name in a menu) is now done in chat instead of an anvil screen. The menu closes, you type the name, and that line never shows up in chat for anyone. Type `cancel` to stop. It drops after 2 minutes.
- **Action log.** Every change made through Claims is written to `<world>/elements_claims/logs/actions-<date>.log`, one file per day, kept for `log.keep_days` (default 30). Staff read and search it with `/claims log` and `/claims log find <text>` (`claims.log` permission). Other mods can listen with `ClaimsAPI.onAction` and add lines with `ClaimsAPI.logAction`.
- New switches in features.toml so everything has an off switch: `deed_cooldown`, `transfer`, `admin_mode`, sub-area `manager` and `removal_countdown`, expiry `playtime_based`, and new `[backups]`, `[alerts]` and `[log]` sections. How many backups are kept is now set in claims.toml `[backups]`.
- A player banned from a claim can't be added to its sub-areas until they're unbanned.
- **Overlap guard.** With Open Parties and Claims, FTB Chunks, Flan or Cadmus installed, Claims starts in import-only mode (no claiming, protection, menus or outlines) and alerts staff. Only importing, `/claims reload`, `/claims log`, backups and alerts stay on. After importing, remove the other mod and restart, and Claims turns on by itself. `[overlap_guard] force_on` in features.toml skips the guard.

### Property limit
- Added a limit on how many properties a player can own. A property is a separate claim with its own name, like a base and a shop in different spots. They all share the player's chunk allowance. The default is 1, and it can't go above 5.
- Server owners set it with `properties.max` in claims.toml. Ranks can change it with the `claims.properties` number node, which is capped at 5 too.
- It can be turned off with the `property_limit` switch in features.toml. It only does anything while `merge` is on, since with merge off every chunk is its own claim.
- Claiming a chunk next to one of your claims still works when you're at the limit. Buying a claim through the market counts as a new property.

### Phase 1 — core
- **Claim Deed** - a paper with a hidden tag (plain paper does nothing), never used up. Right-click claims the chunk you stand in; sneak + right-click unclaims it; right-click inside your own claim shows its info. The lore shows the counter live ("Chunks claimed: 37 / 300"). `/claim deed` gives a new one if lost; optional Deed on first join (`deed_on_first_join`, off by default).
- **Chunk allowance** - starter value in claims.toml (default 100), raised by the `claims.limit` number node (Nexus: `claims.limit=300`). Rank changes show straight away, offline players included. Unclaiming gives the chunk back.
- **Claims** - a chunk claimed next to your own claim joins it, and claims it connects merge. Optional minimum gap between different players' claims (off by default). Dimensions can be closed to claiming.
- **Ranks** - every claim has Owner, Admin, Member and Visitor with the SPEC defaults (Visitor: walk in and use shops only). Rank names, flags, order and extra ranks are stored now and editable from the menu in Phase 2.
- **Trust** - `/trust`, `/untrust`, `/trustlist`. Works for offline players who have joined before. Trusted players go to the lowest trusted rank.
- **Protection** - break/place, using blocks (containers, doors, redstone, farming blocks, anything clickable), using items on blocks (tools, buckets, fire, boats, wrenches...), mobs (trading, riding, farming, attacking players / pets / animals / monsters, including with arrows), armor stands and item frames, item pickup (your own drops always), ender pearls and chorus fruit, entering (bans, private claims), flying and elytra. Blocks and items from other mods are sorted by what they do, so modded containers and machines are covered. Each rank flag has its own switch in features.toml.
- **Staff** - `claims.bypass` builds and edits in any player claim with no toggle; `claims.admin` does the same in admin claims. `/claims info`, `/claims reload`.
- **Outlines** - borders of nearby claims while holding the Deed, plus a white outline around the unclaimed chunk you stand in. Sent only to that player; blocks and radius in claims.toml.
- Land flags are saved with every claim (defaults from the SPEC) and take effect in Phase 3.

### Phase 2 — menus
- **Land Config** - right-click with the Deed inside a claim you manage, or `/claim`. Opens Land Flags, Ranks, Player Management, Bans, Visitor settings, Rename and Private. Players only see the buttons they're allowed to use.
- **Land Flags** - switch each land flag for the claim (they take effect in Phase 3).
- **Ranks** - ranks top (Owner) to bottom (Visitor). Left-click to pick a rank up and left-click where it goes; shift + left / right-click moves it up / down; right-click edits its flags. Add, rename and delete ranks (max in claims.toml, default 10). Deleted ranks' players move one rank down.
- **Rank flags** - click to switch each flag. Giving a rank Manage Players or Admin Mode asks "Are you sure?". Visitor can't get the management flags.
- **Player Management** - every trusted player's head, highest rank first. Promote, demote, remove (with confirm) and ban.
- **Bans** - banned players (click to unban) and "Ban a Player" by name. Banned players are pushed out and can't enter. A ban takes them off every list in the claim: members, sub-areas, sub-area manager and removal countdowns. Blocks they placed in sub-areas become the owner's.
- **Private claims** - only trusted players may enter.
- **Who can do what** - Owner and staff (`claims.bypass`, `claims.admin` in admin claims): everything, and they're the only ones who set flags or change the rank list. Admin Mode: trust, untrust, promote, demote, remove and ban players below their own rank. Manage Players: promote, demote and remove players below their own rank. Manage Land: rename the claim.
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
- **Abandoned claims** (off by default, `expiry.toml`): owner offline longer than their playtime allows (2 weeks at 1 hour played, up to a year at 30 days played, steps in `playtime_days`) → marked abandoned (still protected) → after the grace period freed. Logging in clears it. Exempt players list and `claims.expiry.exempt`.
- **Selling** (off by default, needs Elements: Economy): owners set a price for a claim (Land Config) or a sub-area. Buyers right-click with their Deed inside and confirm; money goes to the owner and ownership transfers (a sub-area buyer gets its rank there for good).
- **Renting sub-areas** (off by default): each owner chooses whether to rent, the price, period (`market.toml`), renter rank and whether leftover items go back to the renter. Paid in advance, renewable; renters are removed when time runs out. Items owed to offline players are handed over on their next login.

### Phase 10 — importers and maps
- `/claims import <opac|ftb|flan|cadmus>` shows a preview; `... confirm` imports (a backup of claims.json is made first). Reads the other mod's files straight from the world, so it doesn't need to be installed. Server claims become admin claims, parties/teams/trusted players become members. Chunks already claimed are skipped.
- **BlueMap** and **squaremap**: claims drawn on the map with name and owner (refreshed 30 s after changes).

### Not included
- JourneyMap overlays need JourneyMap's API library at build time; Xaero's maps can't show other mods' claims without a client mod; Dynmap has no NeoForge 1.21.1 build.
- Nexus side panel / waystone rules / `/sethome` in claims, Elements: Armor Stands and Elements: Economy shop creation need small changes in those mods to call the Claims API.
