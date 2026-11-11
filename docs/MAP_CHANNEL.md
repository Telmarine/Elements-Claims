# Map channel (`elements_claims:map`)

An optional network channel for client map mods (Codex: Cartography uses it). Players without a map mod never get it, and Claims stays server-only. Switch: `features.toml` `[maps] cartography`. Numbers: `claims.toml` `[cartography]`.

Every message is one NBT compound with an `op` string. Chunks are `ChunkPos.toLong()` values. Dimensions are ids like `minecraft:overworld`.

## Server to client

| op | Fields |
| --- | --- |
| `hello` | `claim_range`, `chunks_used`, `chunks_allowed`, `props_used`, `props_max` (sent on join) |
| `counts` | `chunks_used`, `chunks_allowed`, `props_used`, `props_max` (after claims change) |
| `clear` | none: forget every claim (dimension change, reload) |
| `claims` | `dim`, `claims`: list of claims (below) that are new or changed |
| `remove` | `ids`: int array of claims that left range or are gone |
| `result` | `message`: text to show the player (already colored) |

A claim: `id` (int), `name`, `owner`, `standing` (0 own, 1 trusted, 2 other, 3 admin), `rank` (the player's rank name, trusted only), `private` (true when the name and owner are hidden), `color` (claim color, when not private), `chunks` (long array, this dimension only), `manage` (owner or staff: may unclaim), `active` (the player's active property), `subs` (list: `name`, `x1 y1 z1 x2 y2 z2`, trusted only), `marks` (list: `id`, `name`, `emblem`, `color`, `x y z`, only for ranks with View claim marks).

Claims within `range` chunks of the player are sent on join, then only changes, at most once a second.

## Client to server

| op | Fields | What happens |
| --- | --- | --- |
| `claim` | `dim`, `chunks` | Claims the chunks through the same code as the Deed. All or nothing against the chunks the player has left; every chunk must be within `claim_range` of the player and in their dimension. |
| `unclaim` | `dim`, `chunks` | Unclaims, same rules as the Deed (owner, or staff with `claims.bypass` / `claims.admin`). |
| `open_config` | `id` | Opens the Land Config menu, if the player may open it. |
| `set_active` | `id` | Makes one of the player's properties the active one. |
| `mark_add` | `claim`, `name`, `emblem`, `color`, `x y z` | Adds a claim mark (owner or Manage land). |
| `mark_remove` | `claim`, `id` | Removes a claim mark (owner or Manage land). |
| `request` | none | Sends everything again. |

Requests are rate limited (`requests_per_second`) and logged in the action log like the same action from the Deed, with "(map)" added.
