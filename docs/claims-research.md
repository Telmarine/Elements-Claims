# Elements: Claims — research knowledge base

Working knowledge for a land-claiming mod in the Elements family (NeoForge 1.21.1, server-side).
Researched 11 Oct 2026. All research done in the cloud workspace by cloning public repos and reading
their source/config/docs, plus store and wiki pages. Nothing was downloaded to the local computer.

Tags: **[src]** read in cloned source/config · **[page]** store/docs page · **[unverified]** not confirmed.

---

## 1. What exists for NeoForge 1.21.1 (mods)

| | OPAC | FTB Chunks | Flan | Cadmus / Odyssey Claims |
|---|---|---|---|---|
| Side | Server-only works (client adds UI) | Client + server | Server-only (chest GUIs) | Server-only works (client adds map) |
| License | LGPL-3.0-only (no PRs accepted) | All Rights Reserved | All Rights Reserved | MIT |
| Shape | Chunk | Chunk | Box / 3D, any size | Chunk |
| Claim by | `'` UI, Xaero map, `/openpac-claims` | Own map GUI, commands | Golden hoe (corners), stick inspects | Map GUI, `/claim area|shape` |
| Limits | Config + permission node (read only while online), party size bonus, anchors (new) | Config + permission (needs FTB XMod Compat for Ranks/LuckPerms) | **Claim blocks earned by playtime** (1 per 600 ticks) + **buy/sell** | Gamerule or Prometheus only |
| Trust | Party / ally / player groups, ~50 options | Team / ally: Public, Allies, Private | **Per-group, per-flag**, ~70 flags | Member vs non-member |
| Sub-claims | "Sub-configs" | No | Yes | No |
| Pistons / fluids / fire across borders | Yes / Yes / Yes | Yes / opt-in / opt-in (perf warning) | Yes / water only / Yes | ? / Yes / Yes |
| Create | **Deep — 17 mixins** (contraptions, deployers, drills, arms, schematicannon…) | Generic fake-player policy | Stops contraption carts crossing | None |
| Forceload | Yes, offline option | Yes, modes + idle unforce | No | Yes |
| Expiry | Yes (8760 h default, reclaimable) | Yes (default off) | Yes + banned players | No |
| Rent / sale / upkeep | No | No | Rent via Flan-Rental addon (ARR) | No |
| Maps | Xaero (BlueMap only via 3rd-party addon) | Own map (JourneyMap via addon) | BlueMap, Dynmap, JourneyMap | Xaero, JourneyMap |
| Permissions | NeoForge API, LuckPerms, FTB Ranks, Prometheus | FTB Ranks / LuckPerms via XMod | **FTB Ranks only** on NeoForge | Prometheus only |

Other NeoForge 1.21.1 claim/region mods:
- **Holdfast Factions** (MPL-2.0/GPL-3.0, client+server): faction chunk claims via a Territory Table block, must connect, 3 members to claim, **upkeep** (Create: Numismatics coins or ores; outer chunks dropped first after 5-day grace), wars.
- **Faction Friction** (ARR): map claiming, sieges, Xaero display.
- **Claim My Land** (LGPL-3.0): rectangle "parcels" placed with a Deed item + Foundation Stone; Player/Nation/Zone/Citizen parcels; 7 whitelist types; JourneyMap overlays.
- **Claim** by yigitguven (GPL-3.0): chunk claims, BUILD / INTERACT / CONTAINERS / MANAGE trust.
- **YAWP** (AGPL-3.0, server-side): admin region tool, 90+ flags, can delegate regions.
- **WorldGuard (Ported)** (GPL-3.0) and **Jake's World Guard** (MIT, plots sold via SDM-Shop): admin zones.
- **MineColonies**: colony border is its own protection; Flan refuses to claim over colonies.
- Addons worth noting: "Don't Claim our Structures!" (blocks claiming chunks with structures), "Claim Intrusion Alert" (alerts + offline recap when someone is denied in your claim), OPAC-Essentials (Apache-2.0: `/claim` shortcuts, party chat, permission UI).

**Not available:** GriefDefender has no NeoForge/Fabric build (Bukkit/Sponge only).

### The NeoForge protection-API gap
Patbox's **Common Protection API** (MIT) lets machine mods ask "may this happen here?"
(canBreak / canPlace / canExplode / canInteract / canDamage). It is **Fabric-only**. On NeoForge,
claim mods only see what other mods expose as normal events with a FakePlayer, so these bypass
every claim mod today:
- Custom explosions (Supplementaries cannonballs — Flan #341)
- Area tools (FTB Ultimine — FTB #1587), building wands (FTB #849)
- Modded projectiles (Cobblemon balls — OPAC #758; Ars Nouveau — Flan #343)
- Backpack pickup (Sophisticated Backpacks — Flan #437)

---

## 2. Paper/Spigot plugins (player claims)

| | GriefPrevention | GP3D (fork) | GriefDefender | HuskClaims | ClaimChunk | Lands | RedProtect | Residence |
|---|---|---|---|---|---|---|---|---|
| License | GPL-3 | GPL-3 | Paid, closed (old repo MIT, 2021) | Apache-2 (paid binaries) | MIT | Paid, closed | GPL-3 | Paid, no licence file |
| Shape | 2D full-height rect | + 3D subdivisions, shaped, merge | 2D or 3D, all types | 2D full-height | Chunk | Chunk + block sub-areas | 3D cuboid | 3D cuboid, multi-area |
| Claim by | Golden shovel, `/claim r`, auto first-chest claim | + scroll-wheel resize w/ preview | Shovel, `/gd claim` | Shovel, `/claim r` | Command, wool-grid GUI map, flood-fill | GUI, map, walk, radius, tool | Wand, or fence + `[rp]` sign | Hoe + command |
| Budget | Blocks per hour (+Vault buy in v16) | + idle-aware accrual, buy cap | Blocks/hour (only if moved 3+ blocks) or money; per-rank via LP meta | Blocks/hour, per-rank perms (highest wins), Vault, gifting, **change log** | Chunk count per rank, Vault price | Perm limits, grows with members/levels | Per-perm, Vault | Per-group sizes |
| Trust | Access < Container < Build < Manage | + neighbor trust | Accessor / Resident / Container / Builder / Manager | **Configurable levels**; player, @group, #public, LP role | Per-player flags | Custom roles, allies, nations | Leader / admin / member | Per-player/group flags |
| Player flags | Explosions toggle | + wither toggle | Full context flags + options | **Operation groups** + `/claimflags` | 13 flags | Role/natural flags | ~35 + client-mod blocker | ~140 |
| Rent / sell | Addon | Addon | Rent (reworking), sell | No | No | Rent + sell | Addon; abandoned auto-sold | Rent, lease, buy/sell |
| Tax / upkeep | No | No | Daily tax → freeze → delete | No | No | Upkeep, taxes, nations | No | Leases |
| Expiry | 60 d + nature restore (v16) | Same | 14 d, restore to world-gen | 60 d prune, refund | Timer | Delete/transfer, regen | 90 d, WE regen or **sell** | 60 d, regen/transfer |
| Visuals | Fake blocks | + glow | Fake blocks (per-tick cap) | **Glowing BlockDisplay**, colour-coded | Particles | Particles | Particles | BlockDisplay |
| Web map | Addon | — | GDHooks | BlueMap / Pl3x / Dynmap | — | All 4 | Dynmap | All 4 |

Notes:
- GriefPrevention master (v18+) dropped the economy, idle-accrual and siege keys; v16 "Legacy" is the stable line.
- HuskClaims has **Fabric 1.21.x modules** (Apache-2.0) — a usable reference for mixin-based protection hooks.
- Lands: praised for a clean GUI, criticised for huge configs, a 2000-line translation file, claims lost after an update, weak support.
- GriefDefender: very flexible, but "very complicated to configure", hard LuckPerms reliance, closed source.

## 3. Region and town systems

- **WorldGuard** (LGPLv3): cuboid/polygon regions + `__global__`; integer priority; one parent per region; at equal priority **deny wins** for state flags; ~120 flags in 8 categories (protection, mobs/fire/explosions, natural events, movement, map-making, messages). Region groups: all / members / owners / nonmembers / nonowners. Spatial R-tree index + per-chunk cache; fluid/fire flags are opt-in "high-frequency" for performance. Trap: `build deny` on global breaks pistons.
- **Towny** (CC BY-NC-ND 3.0): grid townblocks → towns → nations; claim allowance per resident, escalating claim price, distance rules between homeblocks, upkeep and daily taxes, bankruptcy → **ruins** (timed, then reclaimable), revert-on-unclaim snapshots. Permissions: build / destroy / switch / item_use × resident / nation / ally / outsider. Plot types (shop, arena, embassy, inn, jail, farm, bank). War is an addon (SiegeWar: fixed weekend windows).
- **Factions** (FactionsUUID paid, SaberFactions free GPL-3, MassiveCore dead since 2018): chunk claims backed by power or DTR; buffer zones; overclaim when land > power; offline-raid protection options; role × relation permission selectors.
- **KingdomsX** (core closed, configs MIT): chunk claims around a Nexus block, claim cost in resource points (donated items), tax → bankruptcy → disband, invasions via Champion NPC, turrets.
- **PlotSquared** (GPLv3): plot worlds; owner / trusted / member (only while owner online) / denied; ~98 flags incl. **entity caps per plot**; auto-clear of barely-built plots by "analysis".
- **EarthMC overclaim case study** (Towny): claims never shrank when residents left → inactive towns squatted the map. Fix: towns over their limit can be overclaimed down to it (attacker needs 3 adjacent claims, homeblock protected, 1-month grace). Rejected: war, resets, decay, money upkeep (favours the rich).
- **RetroMC "Claimgate"**: one giant claim crash-looped the server on unclaim → claim and unclaim sizes must be bounded.

---

## 4. Ideas worth taking

1. **Trust levels built from actions** (HuskClaims): Access / Container / Build / Manage shipped as defaults, editable by the owner.
2. **Trust targets**: a player, a player-made group, `public`, a Nexus rank. Plus **neighbor trust** to bypass the minimum distance (GP3D).
3. **Simple toggles for players, full flags for admins** (HuskClaims "operation groups"): e.g. "Explosions on/off", "Visitors can use doors".
4. **Earned claim allowance**: hourly accrual with an idle penalty (GP, GD "moved 3+ blocks"), rank overrides where the highest wins, optional purchase with a cap, and a log of every change.
5. **Shape and editing**: full-height claims by default, 3D sub-claims, merging, resize by dragging a corner with a live preview.
6. **Glowing, colour-coded outlines** sent per player (BlockDisplay entities — vanilla, no client mod), with a per-tick cap.
7. **Starter protection**: auto-claim a small area around a new player's first chest that expires if unused (GP, GD, Lands "camp").
8. **Expiry that doesn't destroy**: inactivity timer → claim becomes "abandoned" (protected core keeps working for a while) → reclaimable or for sale (RedProtect), optional terrain restore.
9. **Anti-squat spacing**: minimum distance between claims, connected-claim rule optional, `/trapped`.
10. **Rate-limited denial messages** (GP3D) and **enter/leave titles** with an anti-spam delay.
11. **Claim bans and private claims** (no entry).
12. **Per-claim options**: no-flight, keep-inventory, **entity/spawn cap per claim** (GD, PlotSquared) — important in a kitchen-sink pack.
13. **Modded-content targets by registry ID and tag with wildcards** (`modid:*`), plus a fake-player policy (deny / named / by UUID), and the OPAC approach of specific Create hooks.
14. **Death-drop locking and pet protection** (HuskClaims).
15. **Optional economy layer**: upkeep per chunk or area, unpaid → freeze → release outer chunks first (Holdfast, GD), rentable sub-claims that restore a snapshot when the lease ends (Residence).
16. **Admin layer in the same engine**: server regions with priority and inheritance (WorldGuard semantics, deny wins at equal priority) instead of running a separate region mod.
17. **Importers** from OPAC (and GP) so a server can switch without re-claiming.
18. **Map hooks**: Xaero, JourneyMap, BlueMap, squaremap, Dynmap; plus a chest-GUI chunk map for vanilla clients (ClaimChunk, Lands).
19. **Intrusion alerts** with an offline recap (Claim Intrusion Alert).

## 5. Pain points and pitfalls to design around

- **Abandoned land / claim inflation** is the biggest long-term problem — tie allowance to activity, protect a core, let the rest decay or become reclaimable.
- **Border griefing**: fluids, pistons, dispensers, fire and projectiles from outside; explosions at the edge.
- **Confusing setup**: huge configs (Lands, GD, FactionsUUID). Ship sensible defaults and a Master Control File.
- **Undefined overlap rules** (WorldGuard non-state flags). Define priority and inheritance explicitly.
- **Large-claim lag and exploits**: cap single claim actions and unclaims, index claims spatially, keep per-tick checks opt-in.
- **Daily batch jobs** (taxes, upkeep, expiry) stall servers — spread them out / run off the main thread.
- **Data loss on updates** (Lands) — UUID keys, versioned storage, backups.
- **Hard dependency on a permissions mod** (GD) — use the NeoForge permission API (Nexus already provides it).
- **Online-only permission reads** (OPAC reads rank limits only while the player is online).
- **Party-forced design**: OPAC party-owned claims can force players into parties before claiming.
- **Modded bypasses** (section 1) — no NeoForge protection API exists; every hook must be added deliberately.

## 6. Licensing notes (for reuse)

- Code reuse only from permissive licences, with credit: Cadmus/Odyssey (MIT), Common Protection API (MIT), ClaimChunk (MIT), HuskClaims (Apache-2.0), OPAC-Essentials (Apache-2.0).
- GPL/LGPL/AGPL (OPAC, GriefPrevention, GP3D, RedProtect, WorldGuard, YAWP, Flan's addons aside): ideas only, no copied code, since Elements mods use the custom TAC5 licence.
- ARR / closed (FTB Chunks, Flan, Lands, GriefDefender, Residence, KingdomsX core): ideas only.
- Towny is CC BY-NC-ND: ideas only.
- Ideas, mechanics and flag *concepts* are not protected; copying code, config layouts word-for-word or names is what to avoid.

---

## Sources

Repos cloned (cloud only): thexaero/open-parties-and-claims, FTBTeam/FTB-Chunks, FTB-Library, FTB-Ranks, FTB-XMod-Compat, Flemmli97/Flan, terrarium-earth/Cadmus, Z0rdak/Yet-Another-World-Protector, Patbox/common-protection-api, GriefPrevention/GriefPrevention (master + legacy/v16), castledking/GriefPrevention3D, bloodmc/GriefDefender (+ wiki), WiIIiam278/HuskClaims, cjburkey01/ClaimChunk, FabioZumbi12/RedProtect, Zrips/Residence, EngineHub/WorldGuard, TownyAdvanced/Towny, drtshock/Factions, SaberLLC/Saber-Factions, MassiveCraft/Factions, IntellectualSites/PlotSquared, CryptoMorin/KingdomsX (+ wiki).

Pages:
- https://modrinth.com/mod/open-parties-and-claims · OPAC issues #816, #758, #731
- FTB-Mods-Issues #1600, #1587, #1974, #849 · https://modrinth.com/mod/journeymap-integration
- https://modrinth.com/mod/flan · Flan issues #437, #341, #343 · https://moddex.gg/mod/flan-rental
- https://moddex.gg/mod/odyssey-claims
- https://github.com/Patbox/common-protection-api
- https://www.modpackindex.com/mod/158478/holdfast-factions-land-claims-upkeep-warzones
- https://www.curseforge.com/minecraft/mc-mods/faction-friction · /claim-my-land · /claim · /sclaims · /worldguard-ported
- https://modrinth.com/mod/yawp · https://www.modpackindex.com/mod/93213/jakes-world-guardprotection-and-plots
- https://moddex.gg/mod/dont-claim-our-structures · https://moddex.gg/mod/claim-intrusion-alert · https://moddex.gg/mod/opac-essentials
- https://minecolonies.com/wiki/systems/protection
- https://hangar.papermc.io/GriefPrevention/GriefPrevention · https://hangar.papermc.io/castledking/GriefPrevention3D
- https://speedgot.ajg0702.us/resources/68900 (GriefDefender) · /53313 (Lands) · /11480 (Residence) · /1035 (FactionsUUID) — and their review pages
- https://builtbybit.com/resources/huskclaims-modern-cross-server-claims.37536
- https://worldguard.enginehub.org/en/latest/regions/flags/ · /priorities/ · /global-region/
- https://earthmc.net/blog/overclaim · https://wiki.retromc.org/Claimgate
- https://github.com/Goosius1/SiegeWar · https://docs.coreprotect.net/commands/
- https://builtbybit.com/threads/whats-a-good-land-claiming-plugin.686326/
