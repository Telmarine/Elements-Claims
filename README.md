# Elements: Claims

Land claiming for NeoForge 1.21.1 servers, in the Elements family (TAC5 Studios / Telmarine).

- Server-side only. Players install nothing (vanilla clients work).
- Claim chunks with the **Claim Deed**, trust players into ranks, protect your land.
- Runs on its own or next to Elements: Nexus (Nexus ranks set the chunk allowance).

## Players
| Action | How |
|---|---|
| Claim the chunk you stand in | Right-click with the Claim Deed |
| Unclaim it | Sneak + right-click with the Deed |
| New Deed if lost | `/claim deed` |
| Land Config menu / your claim list | `/claim`, or right-click with the Deed inside your claim |
| Trust / untrust a player | `/trust <player>` · `/untrust <player>` |
| Who's trusted | `/trustlist` |

Trust commands apply to the claim you stand in, or to all your claims when you're outside them.
Sub-areas: Land Config → Sub-areas → Sub-area Stick, then right-click two corners.
Holding the Deed shows glowing outlines: green yours · aqua trusted · red others · gold admin · white the unclaimed chunk you stand in.

## Staff
- `/claim deed admin` — Admin Deed (admin claims: spawn, hubs, jail)
- `/claims info` — owner, ranks, members and flags of the claim you're in
- `/claims detected` — machines from other mods seen in claims; set each to Owner / Allow / Deny
- `/claims import <opac|ftb|flan|cadmus> [confirm]` — import claims (preview first; backup made)
- `/claims reload` — reload every config file

## Permissions
| Node | Default | For |
|---|---|---|
| `claims.use` | everyone | Deed, `/claim`, `/trust` |
| `claims.limit` (number) | starter value | chunk allowance. Nexus rank entry: `claims.limit=300` |
| `claims.admin` | OP | build and edit in admin claims automatically |
| `claims.bypass` | OP | build and edit in any player claim automatically — senior staff only |
| `claims.info` · `claims.reload` · `claims.detected` · `claims.import` | OP | staff commands |
| `claims.expiry.exempt` | OP | claims never become abandoned |

## Config (`config/elements_claims/`)
- `features.toml` — Master Control File: every switch, including each rank flag
- `claims.toml` — starter allowance, Deed and stick items, claim gap, disabled dimensions, ranks, sub-areas, outline colours
- `limits.toml` — lag and entity limit numbers; `mods/<modid>.toml` — per-mod limits (only for installed mods)
- `expiry.toml` — abandoned claims; `market.toml` — selling and renting
- `detected.toml` — auto-sort results, your overrides, machines
- `messages.toml` — every message, `&` colours
- Claim data: `<world>/elements_claims/claims.json`

## For other mods
`com.tac5studios.elementsclaims.api.ClaimsAPI` — `can(player, level, pos, "build")`, `canBuild`, `canUseShops`, `claimName`, `owner`, `isTrusted`...

## Building
1. Elements: Vault in Maven Local (`gradlew publishToMavenLocal` in `elements-vault`, same as for Economy).
2. `gradlew build`. The jar is `build/libs/elements_claims-<version>.jar`.

Design: `docs/SPEC.md`. Research: `docs/claims-research.md`.
