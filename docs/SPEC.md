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
- Promote / demote / remove: Owner only, or Admins if the Owner allows it. Giving that power to Admins shows an **"Are you sure?"** confirm screen.
- Claim bans and private claims (no entry) are in.

## 2. Menu (decided)
- **Land Config → Land Flags** — claim-wide flags (section 4).
- **Land Config → Ranks** — controls for the lower ranks (rank flags, names, order, add rank).
- **Land Config → Player Management** — player heads with hover names; promote / demote / remove buttons.

## 3. Rank flags (decided — per rank, set by the land owner)
Enter · Build (break + place) · Containers · Doors & Gates · Redstone · Interact · Farming (harvest, plant, shear) · Trading · Vehicles & Riding · Item Pickup · Fire (placing) · Teleport Items (pearls, chorus, wind charges) · Flight (flight + elytra) · Attack Players · Attack Pets · Attack Animals · Attack Monsters · Shops: Use · Shops: Create · Manage Players · Manage Land (claim/unclaim, rename, land flags, lower ranks' flags, set spawn) · Sub-areas.

- Shop creation is the land owner's decision (rank flag, and per sub-area — e.g. a hub owner opening shop plots in rented sub-areas). Shops keep Elements: Economy's own settings and also respect the claim.

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
3. **Live machine detection** — the first time an unknown stand-in (fake) player acts in a claim, record which mod it came from. `/claims detected` lists them; the owner sets each to "acts as the player who placed it" or "deny".

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
- OPAC parties → members at the default trusted rank; server claims → admin claims; claim names and colours kept.

## 8. Server owner controls (decided, details open)
- Claim allowance: set by the server owner only. No earning, no penalties.
- Rank bonuses: server owner config.
- Coloured claim borders: glowing outlines (vanilla display entities, sent per player) shown when holding the Deed or stick and when a chunk is claimed. Colours, changeable by the server owner: own claims green · trusted claims aqua · other players' claims red · admin claims gold · sub-areas yellow · white (temporary preview): the unclaimed chunk you're standing in while holding the Deed, and a sub-area being drawn with the stick before the second corner. Once claimed or finished, the outline switches to its normal colour. A right-click with the Deed claims straight away; unclaim to undo.
- Starter claim = the starting chunk allowance every new player gets (e.g. 100). Set by the server owner in config; rank number nodes raise it from there.
- Minimum distance between claims: on/off switch for the server owner.
- Hostile mob spawns: the land owner allows or blocks them in their claim (Monster Spawning land flag). Outside claims, mobs spawn normally under the server's own spawn limits — the claim mod doesn't touch them.
- Entity limits: optional, switched on and set by the server owner — per claim and/or server-wide. Categories include pets and passive mobs (farm animals, animal farms) as well as monsters, villagers and dropped items. Over a limit, breeding, spawn eggs and spawners stop adding to it and the land owner gets a message; nothing already there is killed. Easy NPCs never count.
- Abandoned claims (all time limits customizable by the server owner):
  1. Owner hasn't logged in for X days → claim marked abandoned; protection stays on for a grace period of Y days.
  2. Grace period ends → claim becomes reclaimable by anyone, or goes up for sale if claim sales are on; the chunks go back to the previous owner's counter.
  3. The server owner can exempt players or ranks (e.g. staff). Admin claims never expire.
- Selling and renting: two separate Master Control File toggles, **both off by default**. When a toggle is off, none of that feature appears in game.
  - **Selling:** a land owner sets a price on a whole claim or a single sub-area from the menu; the buyer pays through Elements: Economy and ownership transfers. If selling is on, abandoned claims go up for sale at a price set by the server owner.
  - **Renting (sub-areas):** even when the server owner turns it on, each land owner decides whether to use it on their claim. The owner sets a price per day or week; the renter gets a chosen rank inside that sub-area only (e.g. shop plots in a hub). Rent is paid in advance; when it runs out and isn't renewed, the renter is removed — returning their items or resetting the area is the land owner's setting. Aimed at active communities / RPG servers.

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
- `/claim deed` — new Claim Deed if lost

**Staff commands**
- `/claim deed admin` — Admin Deed
- `/claims detected` — machines / mods found by auto-detect; allow or deny each
- `/claims import <mod> [confirm]` — OPAC, FTB Chunks, Flan, Cadmus (preview first)
- `/claims info` — owner, ranks and flags of the claim you're in
- `/claims reload`

**Permission nodes** (NeoForge permission API — managed by Nexus ranks)
| Node | Default | For |
|---|---|---|
| `claims.use` | everyone | Deed, stick, menu, /trust |
| `claims.limit` | starter value | chunk allowance (number node; Nexus rank entry `claims.limit=300`) |
| `claims.properties` | `properties.max` (1) | properties (separate named claims) per player (number node, hard cap 5; `property_limit` switch in features.toml) |
| `claims.admin` | OP | Admin Deed; build and edit in admin claims automatically |
| `claims.bypass` | OP | build and edit in any player claim automatically (no toggle), ignore lag limits — give only to senior staff ranks |
| `claims.detected` · `claims.import` · `claims.reload` | OP | staff commands |

**Config files** (`config/elements_claims/`)
- `features.toml` — Master Control File: switches for every feature, flag, lag limit, selling (off), renting (off), minimum distance, entity limits, expiry, importers; per-mod toggles appear only for detected mods
- `claims.toml` — starter allowance, property limit (`properties.max`, 1-5), Deed / stick items, minimum distance value, border colours, admin claim defaults
- `limits.toml` — lag and entity limit numbers
- `expiry.toml` — days inactive, grace period, exemptions
- `market.toml` — sale / rent settings
- `mods/<modid>.toml` — one per detected mod, created automatically
- `detected.toml` — auto-sort results + server owner overrides
- `messages.toml` — all player-facing messages, `&` colours
- Claim data lives in the world folder (like the Nexus store).

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
9. Expiry, selling and renting. **(built)**
10. Importers (OPAC first, then FTB Chunks, Flan, Cadmus); map integrations. **(built)**

## 13. Build notes
- Integrations researched from each mod's 1.21.1 source. Mods without events (Create contraptions, Crystal Tools extras, Tom's connectors) are handled with optional mixins that do nothing when the mod is missing.
- Nexus (side panel claim name, waystone rules, `/sethome`), Elements: Armor Stands and Elements: Economy (Shops: Create) need a small update in those mods to call `ClaimsAPI`.
- Not possible / not included: JourneyMap overlays (needs JourneyMap's API library to build), Xaero's maps (client mod only), Dynmap (no NeoForge 1.21.1 build).
