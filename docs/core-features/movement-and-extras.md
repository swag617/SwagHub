# Movement & Extras

Four small, self-contained modules — double jump, launchpads, teleport bow, and player hider — plus the gamemode/flight persistence fix that keeps flying staff safe across a relog.

## Double Jump (`module: double-jump`)

Defaults on only when `server-role: hub`. Requires `swaghub.doublejump` (default `op`).

```yaml
double-jump:
  power: 1.4
  height: 1.2
  particle: CLOUD
  sound: ENTITY_BAT_TAKEOFF
  bedrock: true
  regions: []
```

Double-tapping space in mid-air (the same client input vanilla flight uses) launches the player forward and up, with a configurable particle/sound. `regions` are two-corner cuboids (same shape as world-protection's `pvp-zones`, no wand UI) where only the **launch effect** is disabled — the underlying flight grant itself is untouched, so region-gating happens at launch time, not continuously.

`bedrock: true` (default) means double jump works for Bedrock players exactly like Java players, since Geyser relays the same double-tap-space input. Set to `false` to disable it specifically for Bedrock players if that input ever proves unreliable on your network — Java players are never affected either way.

### The Flight Stand-Down Contract

Double jump and `/fly` (see [Chat & Utility Commands](chat-and-utility-commands.md)) share an invariant, independently enforced by each module (no shared mutable state): a module only ever **grants** flight when it currently reads `false`, and only ever **revokes** flight for a player it personally granted it to. If flight is already `true` for some other reason (creative/spectator gamemode, another plugin, or the other of these two modules), neither module claims ownership of it — so neither can accidentally revoke a grant it didn't make.

## Launchpads (`launchpads.yml`)

Always enabled regardless of `server-role` — a utility module like proxy/menus.

```yaml
launchpads:
  example-pad:
    world: world
    x: 0
    y: 65
    z: 10
    power: 1.6     # default 1.6
    height: 1.4    # default 1.4
    particle: CLOUD
    sound: ENTITY_SLIME_JUMP
```

Each entry is a single **block location**. Place a real pressure plate (any `*_PRESSURE_PLATE` material) at that exact block; stepping on it launches the player in their current look direction plus upward, with the configured particle/sound. Gated by `hub-worlds`, same as everything else. There's no wand/selection command — coordinates are set by hand, the same "config-only, no UI" choice `pvp-zones` and double-jump's `regions` make.

A malformed entry (missing `world`/`x`/`y`/`z`, non-positive `power`/`height`) logs one specific warning naming the launchpad id; `power`/`height` fall back to their defaults rather than the whole entry being skipped.

## Teleport Bow (`module: teleport-bow`)

Defaults on only when `server-role: hub`. Requires `swaghub.teleportbow` (default `op`). No settings beyond enable/disable and the permission — any bow shot by a permitted player in a hub world becomes a teleport arrow for that one shot, landing the shooter wherever the arrow does.

## Player Hider (`module: player-hider`)

Defaults on only when `server-role: hub`.

```yaml
player-hider:
  cooldown-seconds: 3
```

Cycled via the `[cycle-player-hider]` action — see `items.yml`'s shipped `hider-toggle` example item (an ender eye that cycles state on right-click). Three states, advancing one step per cycle:

```
ALL_VISIBLE → HIDE_OTHERS → RANKS_ONLY → (back to ALL_VISIBLE)
```

`RANKS_ONLY` hides everyone **without** `swaghub.playerhider.alwaysvisible` (default `op`) from the viewer — useful for letting staff stay visible to each other while hidden from regular players. `cooldown-seconds` is a minimum time between cycles for the same player.

> Permission changes made via a permissions plugin while a player is already online are only re-evaluated the next time that specific pair of players triggers a join/quit/cycle event — not instantly. See [Known Limitations](../troubleshooting/known-limitations.md).

## Gamemode & Flight Persistence

`module: player-state` (`PlayerStateModule`) — always enabled, a general safety feature rather than a hub-specific one. Fixes a real live bug: a staff member flying in creative who logs off used to be forced back into `join-settings`' default survival/adventure gamemode on their next join, with no memory of what they were doing — falling out of the sky on relog.

- **On quit:** reads the player's live gamemode, flying state, and fly speed directly off the `Player` object (not from any other module's own tracked state, so it catches vanilla `/gamemode` changes or another plugin's flight grant too) and persists them.
- **On join:** always restores fly speed unconditionally (pure QoL, no safety implication). Restores gamemode + flight **only** when the player is in a hub world, a gamemode was actually saved, and they hold `swaghub.bypass.joingamemode` (default `op`) — otherwise `join-settings`' forced default applies as normal.

```
/flyspeed <1-10> [player]
```

Lives in the same module as `/fly` (see [Chat & Utility Commands](chat-and-utility-commands.md)). Requires `swaghub.command.flyspeed` (self) or `swaghub.command.flyspeed.others` (default `op` for both). Maps the 1–10 integer onto Bukkit's flight-speed range (`speed / 10.0`; 1 = vanilla's own default, 10 = Bukkit's max) and persists the value the same way a restored gamemode does — a chosen fly speed survives a relog without needing `swaghub.bypass.joingamemode`, since it has no safety implication.

## Related Pages

- [Hub Essentials](hub-essentials.md)
- [Chat & Utility Commands](chat-and-utility-commands.md)
- [Join Items](join-items.md) — the `[cycle-player-hider]` action
- [Permissions](../permissions/permissions.md)
