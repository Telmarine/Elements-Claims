# Elements: Claims — Spec

Land-claiming mod in the Elements family (TAC5 Studios / Telmarine). NeoForge 1.21.1, server-side, vanilla clients (chest-menu GUIs, no client mod required).
Research: `docs/claims-research.md`. Decisions are added one topic at a time; sections marked **(open)** are still to be decided.

Guiding rule: **keep it stupid simple** — Lands-level options without Lands-level config.

---

## 1. Trust and ranks (decided)
- Commands are only for trusting: `/trust <player>`, `/untrust <player>`, `/trustlist`. Everything else is in the menu.
- `/trust` puts the player in the **lowest trusted rank**. Works for offline players.
- Trust applies to the whole claim (per sub-area trust comes with sub-areas).
- Default ranks: **Owner, Admin, Member, Visitor**.
  - The land owner names the ranks, adds more ranks and sets each rank's permissions.
  - Reorder by click-to-pick-up / click-to-drop in the chest menu (up/down buttons as backup).
  - Owner is fixed at the top.
- **Visitor** = everyone who isn't a member (like Lands' "Untrusted"). Has its own toggle menu. Default: can walk around and use shops, nothing else.
- Every flag (rank flags, land flags, private, sub-area visitor rules) and the rank list are set by the Owner only.
- **Admin Mode** (rank flag only the Owner sees and sets): that rank can trust, untrust, promote, demote, remove and ban players ranked below it. Manage Players alone: promote, demote, remove. Giving either shows an **"Are you sure?"** confirm screen.
- Claim bans and private claims (no entry) are in.

## 2. Menu (decided)
- **Land Config → Land Flags** — claim-wide flags (section 4).
- **Land Config → Ranks** — controls for the lower ranks (rank flags, names, order, add rank).
- **Land Config → Player Management** — player heads with hover names; promote / demote / remove buttons.

## 3. Rank flags (decided — per rank, set by the land owner)
Enter · Build (break + place) · Containers · Doors & Gates · Redstone · Interact · Farming (harvest, plant, shear) · Trading · Vehicles & Riding · Item Pickup · Fire (placing) · Teleport Items (pearls, chorus, wind charges) · Flight (flight + elytra) · Attack Players · Attack Pets · Attack Animals · Attack Monsters · Shops: Use · Shops: Create · Manage Players (promote, demote, remove) · Manage Land (rename the claim, see its ranks) · Sub-areas · Admin Mode (owner-only toggle: trust, untrust, promote, demote, remove and ban players below their rank).

- Shop creation is the land owner's decision (rank flag, and per sub-area — e.g. a hub owner opening shop plots in sub-areas). Shops keep Elements: Economy's own settings and also respect the claim.

## 4. Land flags (decided — claim-wide, set by the land owner)
PvP · Explosions · Mob Griefing · Outside Griefing (pistons, fluids, dispensers, projectiles from outside the border) · Fire Spread · Fluid Flow · Monster Spawning · Animal Spawning · Crop Trampling · Growth · Decay & Melt · Enter/Leave Titles.

- **PvP:** the land owner can turn off all fighting in the claim, including against pets, passive and hostile mobs. If PvP is off server-wide, that always wins; otherwise the land owner decides.
- Personal flags (per player): see enter/leave messages, receive invites.
- Not included: taxes, land bank, wars, nations (that's what Towny is for), join requests, copper golem flag (not in 1.21.1), hidden No Damage flag. Phantoms are handled by Nexus `/phantoms`. Expiry shield is server config.

## 5. Mod detection (decided)
Three layers so new mods are covered without per-mod code wherever possible:
1. **Standard events** — block break/place, interact, entity interact, explosions, fluids, pistons, fake-player actions. Covers every mod that uses NeoForge's normal events.
2. **Startup auto-sort** (same idea as the Nexus/Economy coin auto-detect) — scan every registered block, entity and item and sort it into a flag by what it is:
   - blocks with an inventory or menu → Containers
   - door / trapdoor / gate types and tags → Doors & Gates; buttons, levers, plates → Redstone
   - tameable mobs → Pets; mobs by spawn category → Animals / Monsters
   - rideable entities and vehicles → Vehicles & Riding
   - anything else clickable → Interact
   - The server owner can override any entry.
3. **Live machine detection** — the first time an unknown stand-in (fake) player acts in a claim, record which mod it came from. `/claims detected` lists them; staff set each to "acts as the player who placed it" (default), "allow" or "deny". Stand-in players are judged by who placed the machine, never by where the stand-in stands (Create's deployer puts its stand-in 1.5 blocks in front of itself). The land owner's own machines work even from outside the claim; machines whose placer is unknown are denied unless set to "allow".

Known-mod hooks switch on only when that mod is installed (detected by mod ID at startup). These are needed for mods that change the world without firing events (custom explosions, area tools, contraptions).

## 6. Integrations (decided)

### Create (rule)
- Machines inside a claim **always run** — contraptions, deployers, drills, harvesters, ploughs, mechanical arms, trains, schematicannon. The claim never stops them.
- The claim only blocks griefing by untrusted players: breaking, wrenching, configuring, or taking items from machines.
- **Blocked:** a contraption or machine placed outside the claim by an untrusted player that drills, deploys or harvests across the border into the claim.
- Supported Create addons follow the same rule: Copycats, Create Deco, Bits 'n' Bobs, Contraption Terminals, Rechiseled Create, Sophisticated Backpacks Create Integration.

### Supported mods
| Mod | Hook |
|---|---|
| Sophisticated Backpacks / Core | Register our check with its public hook `WorldHelper.addAdditionalInteractionCheck` |
| Supplementaries | Hook its `ClaimCompat` (canBreak / canPlace / canReplace / canAttack / canInteract) so cannons, slingshots, ropes, flails respect claims |
| Crystal Tools | Claim check on every extra block from vein mining and Tree Chopper |
| Waystones | Placing / using waystones in claims; works alongside Nexus waystone rules |
| Gravestone | The grave's owner can always open their grave |
| Lootr | Lootr chests stay open to visitors |
| Xyndra's Crates (dye baskets) | Crate opening with keys (replaces the OPAC exception) |
| Easy NPC | Visitors can talk to NPCs; only the land owner can edit them |
| Elements: Economy, Spudacious Shops | Shops: Use / Create flags |
| Tom's Storage | Terminals reaching chests across a claim border |
| Butchery | The carcass system (hanging, skinning, processing) always works for the land owner and allowed ranks in every claim; its stand-in player acts as the player using it |
| ReAnimal, Moonlight | Stand-in players — check what each does before writing hooks |
| Piggyback, SitOnEverything, furniture seats | Vehicles & Riding / Interact |
| Apothic Spawners | Spawner editing under Build |
| WorldEdit | Admin bypass |
| Elements: Nexus | Side panel claim name, claim limits from rank number nodes, waystone rules, `/sethome` inside other players' claims |
| Elements: Armor Stands | Switch its protection check from OPAC/FTB reflection to the Claims API |
| Xaero's Minimap / World Map | Showing our claims needs research (Xaero's claim display is built for OPAC) |

### More supported mods
| Mod | Hook |
|---|---|
| Carry On | Blocks need Build/Containers; mobs need the matching Attack flag; shop stock, crate baskets, Easy NPCs and waystones/warp plates can never be picked up in claims |
| Sophisticated Storage (+ Create Integration) | Containers, via the same Sophisticated Core hook; contraption storage follows the Create rule |
| Create: Enchantment Industry | Machines always run; using them is Containers |
| Create: Interiors, Comforts | Seats, sleeping bags, hammocks → Interact |
| Create: Connected, Create Encased, Every Compat, Amendments | Normal Build / Interact / Containers (Amendments cauldrons and hanging signs → Interact) |
| Aquaculture 2 | Hooking mobs/items with a rod → Attack Animals / Item Pickup |
| Friends & Foes | Its mobs follow Mob Griefing and spawning flags; copper golem button presses checked (verify in the jar) |
| Dye The World, Polymorph, AppleSkin, Create Better FPS | No hook needed |

### Other integrations
- Web maps: BlueMap, squaremap, Dynmap (server-side).
- JourneyMap (server-side API, as Flan does).
- MineColonies — no claims over colonies.
- FTB Ultimine — claim check on area mining.
- **Public Elements: Claims API** ("can this player do X here?") for other mods and the Elements mods — NeoForge has no shared protection API.

## 7. Migration (decided)
- Importers for every claim mod available on 1.21.1 so existing claims carry over intact: **OPAC** (first), **FTB Chunks**, **Flan**, **Cadmus / Odyssey Claims**, plus Claim My Land if worth it.
- Every importer brings a player's land in as ONE property holding their chunks in every dimension (it becomes their active property if they had none). The other mod's sub-claims become sub-areas inside it with their own trusted players. Server/admin claims become admin claims (OPAC: one per server sub-claim, named like in OPAC).
- OPAC: settings are read through OPAC's own chain (sub-claim → player config → serverconfig default-player-config → OPAC default). fullAccess: P = party, A = party + allies, E = everyone (Visitors get every flag except the Manage ones), custom groups matched by exact id with included groups followed; protectClaimedChunks off also means everyone. Protection options become land flags: playersByPlayers → PvP, blocksByExplosions → Explosions, fireSpread → Fire Spread, cropTrample → Crop Trampling, naturalSpawnHostile/Friendly → Monster/Animal Spawning, overrideMobGriefingRule → Mob Griefing (off), piston/fluid/dispenser barriers → Outside Griefing (off).
- Flan: boxes rounded out to whole chunks; sub-claims become sub-areas with their exact box. Cadmus: no dimension stored, so everything goes to the overworld.

## 8. Server owner controls (decided, details open)
- Claim allowance: set by the server owner only. No earning, no penalties.
- Rank bonuses: server owner config.
- Coloured claim borders: thin full-bright outlines with no glow (vanilla block displays, sent per player) shown when holding the Deed or stick and when a chunk is claimed. Colours, changeable by the server owner: own claims green · trusted claims light blue · other players' claims red · admin claims orange · sub-areas yellow · white (temporary preview): the unclaimed chunk you're standing in while holding the Deed, and a sub-area being drawn with the stick before the second corner. Once claimed or finished, the outline switches to its normal colour. A right-click with the Deed claims straight away; unclaim to undo.
- Starter claim = the starting chunk allowance every new player gets (e.g. 100). Set by the server owner in config; rank number nodes raise it from there.
- Minimum distance between claims: on/off switch for the server owner.
- Hostile mob spawns: the land owner allows or blocks them in their claim (Monster Spawning land flag). Outside claims, mobs spawn normally under the server's own spawn limits — the claim mod doesn't touch them.
- Entity limits: optional, switched on and set by the server owner — per claim and/or server-wide. Categories include pets and passive mobs (farm animals, animal farms) as well as monsters, villagers and dropped items. Over a limit, breeding, spawn eggs and spawners stop adding to it and the land owner gets a message; nothing already there is killed. Easy NPCs never count.
- Abandoned claims (all time limits customizable by the server owner):
  1. Owner hasn't logged in for longer than their playtime allows → claim marked abandoned; protection stays on for a grace period of Y days. Playtime steps (vanilla play-time stat): 1h=14 days, 4h=30, 12h=60, 1d=120, 7d=180, 14d=270, 30d=365.
  2. Grace period ends → claim becomes reclaimable by anyone; the chunks go back to the previous owner's counter.
  3. The server owner can exempt players or ranks (e.g. staff). Admin claims never expire.
- **No built-in land market (decided 13 Oct 2026).** Claims never touches money; Elements: Economy only connects through player shops. Land deals happen between players. When they've agreed and paid, the owner runs `/claim <property> transfer <player>` and confirms. The receiver must be online and have enough free chunks (total allowance always applies). Both sides see the chunk count; only the receiver sees their own free chunks. The receiver then chooses: add it to their active property (its members, ranks and flags are dropped, chunks and sub-areas move over) or, if they have a free property slot, keep it as its own property. Their active property stays active.
- **Properties are never merged.** Each player has one active property, and every chunk they claim goes to it, in any dimension (a property can have chunks in the Overworld, Nether and End). A new property is only started on purpose with `/claim create <name>` (or by a player's first claim), within the property limit. Switch the active one with `/claim <property> manage` (or right-click it in the claim list). Sub-areas belong to one dimension each. Admin claims join the oldest admin claim they touch.
- **Removing someone from a sub-area:** blocks placed in a sub-area are recorded with who placed them. If the owner removes a player whose blocks are still there, the removal is put on hold and a countdown starts (`sub_areas.leave_days`, default 7). The player keeps their sub-area rank to collect their things. When they've taken everything, or time runs out, they're removed and what's left belongs to the owner. The owner can shift-click to stop the countdown.

## 8b. Lag limits (decided)
- Placement limits for blocks and entities known to cause lag, per chunk or per claim. Each limit has its own switch in the Master Control File (features.toml) and a number.
- Default vanilla list: armor stands, item frames / glow frames, minecarts, boats, paintings · hoppers, observers, pistons / sticky pistons, comparators, repeaters, droppers / dispensers, redstone lamps · furnaces / blast furnaces, beacons, spawners. The server owner can add any block or entity by ID or tag.
- Over a limit: placing more is refused with a message (e.g. "Hopper limit reached (32/32) in this chunk"). Nothing already placed is removed or switched off.
- Exempt: admin claims and staff with a bypass permission.
- **Per-mod limits** (e.g. Create machines) are a separate option: each detected mod gets its own Master Control File toggle and its own config file.
- **Rule: per-mod toggles and config files are generated only for mods the auto-detect finds installed.** No toggle or config ever appears for a mod that isn't detected.
- Limits only stop new placements — Create machines and contraptions already placed always keep running.

## 8c. Admin claims (decided)
- For spawn and any area the server owner doesn't want destroyed (spawn, hubs, jail…).
- Don't count against any allowance; no size limit; never expire.
- An admin claim always wins: players can never claim inside or overlapping one (no exceptions).
- Created by staff with an **Admin Deed** (`/claim deed admin`, permission-gated) — right-click claims the chunk as an admin claim.
- **Staff with the admin permission can build, break and edit everything in admin claims automatically** — no command or mode switch needed to edit (fixes OPAC's biggest annoyance).
- Same Land Flags / Ranks menus, plus admin-only extras: keep inventory, no hunger, block entry / exit, enter / leave commands.
- **Visitor defaults in admin claims — on by default, still toggleable in the admin menu:** ender chests and community chests, crates, NPC shops (Easy NPC traders, shop NPCs).
- The OPAC importer turns existing OPAC server claims into admin claims.

## 9. Claiming method (decided)
- **Claim Deed** — a paper item with a hidden tag (plain paper does nothing). Never consumed; `/claim deed` gives a new one if lost. Handed out on first join (optional), `/claim deed`, or a server-owner config option.
  - Right-click: claim the chunk you're standing in (shows its border).
  - Sneak + right-click: unclaim that chunk.
  - Right-click in the air while looking at your own claim: open the Land Config menu.
- **Claim counter** — kept on the player (not the item); the Deed's lore shows it live, e.g. "Chunks claimed: 37 / 300".
  - Allowance comes from Nexus rank number nodes (e.g. 100 base, +100 per rank) or server config.
  - Unclaiming gives the chunk back; a full counter blocks new claims with a clear message.
  - Rank changes apply straight away, offline players included.
- **Stick** — sub-areas: click two corners inside your own chunks for a block-precise sub-area (shop plots, rented rooms, farms with their own rules). Sub-areas don't use chunk claims.
- No golden shovel / hoe. Items are configurable by the server owner (like Armor Stands' tool).

## 10. Commands, permissions, config (decided)

**Player commands** (minimum — everything else is in the menu)
- `/trust <player>` · `/untrust <player>` · `/trustlist`
- `/trust <player> <subarea> <rank>` · `/untrust <player> <subarea>` — rank inside one sub-area only (claim rank unchanged)
- `/claim` — opens Land Config for the claim you're in (or your claim list)
- `/claim deed` — new Claim Deed if lost (30 min cooldown)
- `/claim create <name>` — start a new property on the chunk you stand in
- `/claim <property> manage` — make it your active property
- `/claim <property> transfer <player>` — give it to another player (confirm screen)

**Staff commands**
- `/claim deed admin` — Admin Deed
- `/claims detected` — machines / mods found by auto-detect; allow or deny each
- `/claims import <mod> [confirm]` — OPAC, FTB Chunks, Flan, Cadmus (preview first)
- `/claims info` — owner, ranks and flags of the claim you're in
- `/claims reload`
- `/claims log [page]` · `/claims log find <text> [page]` — action log, newest first

**Permission nodes** (NeoForge permission API — managed by Nexus ranks)
| Node | Default | For |
|---|---|---|
| `claims.use` | everyone | Deed, stick, menu, /trust |
| `claims.limit` | starter value | chunk allowance (number node; Nexus rank entry `claims.limit=300`) |
| `claims.properties` | `properties.max` (1) | properties (separate named claims) per player (number node, hard cap 5; `property_limit` switch in features.toml) |
| `claims.admin` | OP | Admin Deed; build and edit in admin claims automatically |
| `claims.bypass` | OP | build and edit in any player claim automatically (no toggle), ignore lag limits — give only to senior staff ranks |
| `claims.detected` · `claims.import` · `claims.reload` · `claims.log` | OP | staff commands |

**Overlap guard:** another claim mod installed (OPAC, FTB Chunks, Flan, Cadmus) → import-only mode: everything off except importers, `/claims reload`, `/claims log`, backups and alerts; staff alert with the import command. Removing the other mod and restarting turns Claims on. `[overlap_guard] force_on` skips it.

**Config files** (`config/elements_claims/`)
- `features.toml` — Master Control File: switches for every feature, flag, lag limit, minimum distance, entity limits, expiry, importers, backups, staff alerts and the action log (with one switch per log category); per-mod toggles appear only for detected mods
- `claims.toml` — starter allowance, property limit (`properties.max`, 1-5), Deed / stick items, minimum distance value, outline blocks, map colours, backups kept, log days kept, admin claim defaults
- `limits.toml` — lag and entity limit numbers
- `expiry.toml` — playtime steps (or flat days inactive), grace period, exemptions
- `mods/<modid>.toml` — one per detected mod, created automatically
- `detected.toml` — auto-sort results + server owner overrides
- `messages.toml` — all player-facing messages, `&` colours
- Claim data lives in the world folder (like the Nexus store), with hourly (last 5) and daily (last 7) backups in `elements_claims/backups/`. If claims.json can't be read, the newest backup that works is loaded and staff are alerted; if none works, saving is paused.
- Staff alerts go to the log, online staff with `claims.admin`, and optionally a Discord webhook with a role ping (`[alerts]` in claims.toml).
- Action log: `<world>/elements_claims/logs/actions-YYYY-MM-DD.log`, one line per change (`time | category | who | claim (owner) | action | details`). Categories: claiming, members, bans, settings, sub_areas, transfers, staff, system. Written on a background thread; files older than `log.keep_days` deleted at startup. Staff bypass use is logged once per player, claim and flag every 10 minutes. API: `ClaimsAPI.onAction`, `ClaimsAPI.logAction`.

## 11. Still to decide (open)
- Nothing open

## 12. Build phases
1. **Core** — project, claim store, Claim Deed + counter, `/claim`, `/claim deed`, `/trust`, `/untrust`, `/trustlist`, protection with the 4 default ranks, outlines. **(built)**
2. Menus — Land Config: Land Flags, Ranks (rename, add, reorder, flags), Player Management; Visitor menu; bans and private claims. **(built)**
3. Land flags working in game — PvP, explosions, spawning, fluids, fire, growth, titles; personal flags. **(built)**
4. Admin claims — Admin Deed, automatic staff editing, admin extras, Visitor defaults. **(built)**
5. Auto-detect — startup auto-sort, live machine detection, `detected.toml`, `/claims detected`. **(built)**
6. Sub-areas (stick). **(built)**
7. Integrations — Create rule, mod hooks, public API, Nexus and Armor Stands. **(built)**
8. Lag and entity limits; per-mod config for detected mods. **(built)**
9. Expiry. **(built)** Selling and renting were removed on 13 Oct 2026.
10. Importers (OPAC first, then FTB Chunks, Flan, Cadmus); map integrations. **(built)**

## 13. Build notes
- Integrations researched from each mod's 1.21.1 source. Mods without events (Create contraptions, Crystal Tools extras, Tom's connectors) are handled with optional mixins that do nothing when the mod is missing.
- Nexus (side panel claim name, waystone rules, `/sethome`), Elements: Armor Stands and Elements: Economy (Shops: Create) need a small update in those mods to call `ClaimsAPI`.
- Not possible / not included: JourneyMap overlays (needs JourneyMap's API library to build), Xaero's maps (client mod only), Dynmap (no NeoForge 1.21.1 build).
