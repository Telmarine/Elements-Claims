# Elements: Claims

Elements: Claims is a server-side land claiming mod for NeoForge 1.21.1, part of the Elements family by Telmarine.

Players claim chunks with a Claim Deed and trust other players into ranks they set up themselves.

The mod is stand-alone and has no dependencies. Every player gets the starter chunk allowance from `claims.toml`. I built it this way so server owners and devs can hook in their own ranks. Permissions go through NeoForge's permission API, so any permission mod that sets the `claims.limit` number node can give each rank its own allowance. Elements: Nexus is one option, but you don't need it.

## Players

### Claiming land
Hold the Claim Deed and right-click to claim the chunk you're standing in. Sneak and right-click to unclaim it. If you lose your Deed, `/claim deed` gives you a new one. You can only use it once every 30 minutes (server owners can change this).

Each separate claim is a property with its own name, ranks and flags. All of your properties share the same chunk allowance. By default each player can own 1 property, so new chunks need to touch the one you already have. Server owners can raise this to a max of 5, so you could have a base in one place and a shop somewhere else.

Run `/claim`, or right-click with the Deed inside your claim, to open the Land Config menu. Outside your claims, `/claim` shows your claim list instead.

### Trusting players
Use `/trust <player>` and `/untrust <player>` to add or remove someone, and `/trustlist` to see who's trusted.

If you run `/trust` while standing in one of your claims, the player is only trusted in that claim. If you run it outside your claims, they get trusted in all of them.

### Sub-areas
Open Land Config and go to Sub-areas to get the Sub-area Stick. Right-click two corners with the stick to mark out the area. A sub-area can be any size inside your claim.

You can give a player a different rank inside a sub-area than they have in the rest of the claim. For example, someone can be a Member in your claim but have a Shop Owner rank you made just inside their shop. Stand in your claim and use:
- `/trust <player> <subarea> <rank>` to give them that rank in the sub-area only
- `/untrust <player> <subarea>` to take them out of the sub-area (their claim rank stays the same)

Put names with spaces in quotes, like `/trust Steve "Shop 1" "Shop Owner"`. Sub-area and rank names tab-complete.

### Claim outlines
When you hold the Claim Deed, nearby claims get a glowing outline:
- Green: your claims
- Aqua: claims you're trusted in
- Red: other players' claims
- Gold: admin claims
- White: the unclaimed chunk you're standing in

## Staff

`/claim deed admin` gives you the Admin Deed. Admin claims are meant for places like spawn, hubs or a jail.

`/claims info` shows the owner, ranks, members and flags of the claim you're standing in.

`/claims detected` lists machines from other mods that have acted inside claims. You can set each one to Owner, Allow or Deny.

`/claims import <opac|ftb|flan|cadmus>` shows a preview of what will be imported from another claim mod. Add `confirm` to run the import. A backup is made first.

`/claims reload` reloads the config files.

## Permissions

| Node | Default | What it does |
|---|---|---|
| `claims.use` | Everyone | Use the Deed, `/claim` and `/trust` |
| `claims.limit` | Starter value | Number node for the chunk allowance, set by your permission mod. Elements: Nexus example: `claims.limit=300` |
| `claims.properties` | `properties.max` | Number node for how many properties a player can own. Capped at 5 no matter what it's set to. Elements: Nexus example: `claims.properties=3` |
| `claims.admin` | OP | Build and edit in admin claims |
| `claims.bypass` | OP | Build and edit in any player's claim. I'd only give this to senior staff. |
| `claims.info`, `claims.reload`, `claims.detected`, `claims.import` | OP | Staff commands |
| `claims.expiry.exempt` | OP | Your claims never get marked as abandoned |

## Config

All config files are in `config/elements_claims/`.

- `features.toml` is the Master Control File. Every feature and rank flag can be switched on or off here.
- `claims.toml` has the starter allowance, the property limit (`properties.max`, default 1, max 5), the Deed and stick items, the minimum gap between claims, disabled dimensions, ranks, sub-areas and outline colours.
- `limits.toml` has the lag and entity limits. Per-mod limits go in `mods/<modid>.toml`, which only gets created for mods that are installed.
- `expiry.toml` controls abandoned claims.
- `market.toml` controls selling and renting.
- `detected.toml` has the auto-sort results, your overrides and detected machines.
- `messages.toml` has every player message. Use `&` for colours.

Claim data is saved in `<world>/elements_claims/claims.json`.

## For other mods

Other mods can check claims through `com.tac5studios.elementsclaims.api.ClaimsAPI`. For example, `can(player, level, pos, "build")` checks a permission. There's also `canBuild`, `canUseShops`, `claimName`, `owner` and `isTrusted`.

## Building

1. Publish Elements: Vault to Maven Local by running `gradlew publishToMavenLocal` in the `elements-vault` project.
2. Run `gradlew build`. The jar ends up in `build/libs/elements_claims-<version>.jar`.

The full design is in `docs/SPEC.md` and the research notes are in `docs/claims-research.md`.
