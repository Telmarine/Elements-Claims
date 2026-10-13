# Elements: Claims

Elements: Claims is a server-side land claiming mod for NeoForge 1.21.1, part of the Elements family by Telmarine.

Players claim chunks with a Claim Deed and trust other players into ranks they set up themselves.

The mod is stand-alone and has no dependencies. Every player gets the starter chunk allowance from `claims.toml`. I built it this way so server owners and devs can hook in their own ranks. Permissions go through NeoForge's permission API, so any permission mod that sets the `claims.limit` number node can give each rank its own allowance. Elements: Nexus is one option, but you don't need it.

## Players

### Claiming land
Hold the Claim Deed and right-click to claim the chunk you're standing in. Sneak and right-click to unclaim it. If you lose your Deed, `/claim deed` gives you a new one. You can only use it once every 30 minutes (server owners can change this).

Each separate claim is a property with its own name, ranks and flags. All of your properties share the same chunk allowance. Your first claim starts your first property. By default each player can own 1 property, and server owners can raise this to a max of 5, so you could have a base in one place and a shop somewhere else.

Every chunk you claim goes to your active property, wherever it is, in the Overworld, the Nether or the End. Properties never merge. To start another property, stand where you want it and run `/claim create <name>`. It becomes your active property. To switch which one is active, use `/claim <property> manage` or right-click it in your claim list.

To give a property to another player, run `/claim <property> transfer <player>` and confirm. They have to be online, and they need enough free chunks for it under their total chunk allowance. Both of you see how many chunks are being transferred, and the receiver also sees their own free chunks. They then pick whether to add it to their current property or, if they have a free property slot, keep it as its own property. Adding it to their property drops its old members and settings. Claims doesn't handle money, so sort out the deal between yourselves first.

Run `/claim`, or right-click with the Deed inside your claim, to open the Land Config menu. Outside your claims, `/claim` shows your claim list instead.

### Trusting players
Use `/trust <player>` and `/untrust <player>` to add or remove someone, and `/trustlist` to see who's trusted.

If you run `/trust` while standing in one of your claims, the player is only trusted in that claim. If you run it outside your claims, they get trusted in all of them.

Ranks are set up in Land Config → Ranks. Every flag, for every rank, the land flags and sub-area visitor rules, is set by you only. To get help running things, give one rank **Admin Mode** (only you see that toggle). Players in that rank can trust, untrust, promote, demote, remove and ban anyone ranked below them, and nothing else. Manage Players on its own only allows promote, demote and remove.

Banning someone (Land Config → Bans) takes them off every list in that claim, sub-areas included. Anything they placed in a sub-area becomes yours. If they're standing in the claim, they get moved just outside it.

### Sub-areas
Open Land Config and go to Sub-areas to get the Sub-area Stick. Right-click two corners with the stick to mark out the area. A sub-area can be any size inside your claim.

Only the claim owner can give a player a rank inside a sub-area, which can be different from their rank in the rest of the claim. For example, someone can be a Member in your claim but have a Shop Owner rank you made just inside their shop. Stand in your claim and use:
- `/trust <player> <subarea> <rank>` to give them that rank in the sub-area only
- `/untrust <player> <subarea>` to take them out of the sub-area (their claim rank stays the same)

Put names with spaces in quotes, like `/trust Steve "Shop 1" "Shop Owner"`. Sub-area and rank names tab-complete.

Blocks placed in a sub-area are recorded with who placed them. If you remove someone whose blocks are still there, they aren't removed right away. They get 7 days (server owners can change this) to collect their things, and keep their sub-area rank until then. Once they've taken everything, or the time is up, they're removed and whatever is left is yours. Shift-click them in the sub-area's player list to stop the countdown.

The owner can pick one player from a sub-area as its manager in the sub-area menu. The manager can add, remove and rank players in that sub-area while the owner is away, but only with ranks below their own rank in the sub-area, and they can't change their own rank. They don't need to be trusted in the rest of the claim.

### Claim outlines
When you hold the Claim Deed, nearby claims get a thin outline at your feet height:
- Green: your claims
- Light blue: claims you're trusted in
- Red: other players' claims
- Orange: admin claims
- Yellow: sub-areas
- White: the unclaimed chunk you're standing in

The outline doesn't glow, so players and blocks in front of it hide it like any other block.

## Staff

`/claim deed admin` gives you the Admin Deed. Admin claims are meant for places like spawn, hubs or a jail.

`/claims info` shows the owner, ranks, members and flags of the claim you're standing in.

`/claims detected` lists machines from other mods that have acted inside claims. You can set each one to Owner, Allow or Deny.

**Switching from another claim mod:** if Open Parties and Claims, FTB Chunks, Flan or Cadmus is installed, Claims starts in import-only mode, so the two never protect the same land. Claiming, protection, menus and outlines stay off, and staff get an alert. Run the import, remove the other mod and restart. Claims turns itself on once the other mod is gone. To run both anyway, set `force_on = true` under `[overlap_guard]` in features.toml.

`/claims import <opac|ftb|flan|cadmus>` shows a preview of what will be imported from another claim mod. Add `confirm` to run the import. A backup is made first. Each player's land comes in as one property with their chunks in every dimension, and the other mod's sub-claims become sub-areas. OPAC trust settings and protection options (PvP, explosions, fire spread and so on) carry over too.

`/claims reload` reloads the config files.

`/claims log` shows the action log, newest first. `/claims log find <text>` searches it for a player, claim or action, like `/claims log find Steve`. Add a page number to go further back.

## Permissions

| Node | Default | What it does |
|---|---|---|
| `claims.use` | Everyone | Use the Deed, `/claim` and `/trust` |
| `claims.limit` | Starter value | Number node for the chunk allowance, set by your permission mod. Elements: Nexus example: `claims.limit=300` |
| `claims.properties` | `properties.max` | Number node for how many properties a player can own. Capped at 5 no matter what it's set to. Elements: Nexus example: `claims.properties=3` |
| `claims.admin` | OP | Build and edit in admin claims |
| `claims.bypass` | OP | Build and edit in any player's claim. I'd only give this to senior staff. |
| `claims.info`, `claims.reload`, `claims.detected`, `claims.import`, `claims.log` | OP | Staff commands |
| `claims.expiry.exempt` | OP | Your claims never get marked as abandoned |

**A warning about `claims.admin`:** admin claims can run commands when a player walks in or out, and those commands run as the server console with full OP. Anyone with `claims.admin` can add them, so anyone with that node can effectively run any command on your server, including `op` on themselves. Only give `claims.admin` to people you'd trust with the console. If you'd rather nobody uses this, set `enter_leave_commands = false` under admin claims in features.toml (the Master Control File).

## Config

All config files are in `config/elements_claims/`.

- `features.toml` is the Master Control File. Every feature, rank flag and land flag can be switched on or off here, including backups, staff alerts and the action log.
- `claims.toml` has the starter allowance, the property limit (`properties.max`, default 1, max 5), the Deed and stick items, the minimum gap between claims, disabled dimensions, ranks, sub-areas, outline blocks and map colours.
- `limits.toml` has the lag and entity limits. Per-mod limits go in `mods/<modid>.toml`, which only gets created for mods that are installed.
- `expiry.toml` controls abandoned claims (off by default). How long an owner can be offline depends on how much they've played, from 2 weeks at 1 hour played up to a year at 30 days played. A claim that gets marked abandoned is still protected for the grace period, then freed.
- `detected.toml` has the auto-sort results, your overrides and detected machines.
- `messages.toml` has every player message. Use `&` for colours.

Claim data is saved in `<world>/elements_claims/claims.json`. A copy goes into `backups/` there every hour (the last 5 are kept) and every day (the last 7 are kept). If claims.json ever can't be read, the newest working backup is loaded and staff get an alert. If none of them work, saving is paused so nothing gets overwritten.

Staff alerts show up in the log and in chat for staff with `claims.admin`. To also get them in Discord, set `discord_webhook` and `discord_role_id` under `[alerts]` in claims.toml.

### Action log
Every change made through Claims gets one line in `<world>/elements_claims/logs/actions-<date>.log`: chunks claimed and unclaimed, properties made, renamed and transferred, players trusted, promoted, banned, flag and rank changes, sub-areas and their players, admin claim settings, imports, abandoned claims, restores from backup, and staff using `claims.bypass` in someone else's claim. Each line looks like this:

```
2026-10-13 10:48:01 | members | Steve | Steve's Base (Steve) | trusted | Alex as Member
```

There's one file per day, and files older than `log.keep_days` in claims.toml (default 30) get deleted. Each category can be switched off under `[log]` in features.toml.

## For other mods

Other mods can check claims through `com.tac5studios.elementsclaims.api.ClaimsAPI`. For example, `can(player, level, pos, "build")` checks a permission. There's also `canBuild`, `canUseShops`, `claimName`, `owner` and `isTrusted`.

For the action log, `ClaimsAPI.onAction(entry -> ...)` gets every logged action as it happens, and `ClaimsAPI.logAction(...)` writes your own line to it.

## Building

Run `gradlew build`. The jar ends up in `build/libs/elements_claims-<version>.jar`.

The full design is in `docs/SPEC.md` and the research notes are in `docs/claims-research.md`.
