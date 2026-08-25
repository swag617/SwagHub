# Hub Essentials

Three modules — `spawn`, `world-protection`, and `join-settings` — cover the baseline behavior every hub server needs. All three are gated by `hub-worlds` in `config.yml` (empty list = every world) except `spawn-on-join`/`spawn-on-respawn`, which are deliberately **not** world-gated (see below).

## Spawn / Lobby (`module: spawn`)

Always enabled regardless of `server-role` — utility behavior, never named in the hub-behavior default-off list, and required to keep working even when SwagCore is also installed.

Only **one global lobby location** is supported (no per-world lobbies). Set it with:

```
/setlobby
```

This writes the exact position (world, x/y/z/yaw/pitch) to `plugins/SwagHub/data/spawn.yml` **the instant you run the command** — never only on server shutdown. That immediacy is the specific DeluxeHub bug class SwagHub was built to avoid: a hard server crash or `kill -9` between setting the lobby and the next graceful shutdown can no longer lose your lobby location.

```
/lobby
```

Teleports you to the lobby. Behavior is config-driven:

| Setting | Default | Effect |
|---|---|---|
| `spawn.lobby-teleport-delay-ticks` | `60` (3s) | Ticks `/lobby` waits before teleporting. `0` = instant, no countdown message. |
| `spawn.cancel-on-move` | `true` | Moving during the countdown cancels the teleport. |
| `spawn.spawn-on-join` | `true` | Teleports every player to the lobby the instant they join. |
| `spawn.spawn-on-void-fall` | `true` | Falling into the void in a hub world teleports back to the lobby instead of dying. **This one is gated by `hub-worlds`.** |
| `spawn.spawn-on-respawn` | `true` | Sends players back to the lobby on respawn instead of their bed/anchor. |

`swaghub.bypass.lobbydelay` (default `op`) skips the countdown and move-cancel entirely.

> `spawn-on-join`/`spawn-on-respawn` are **not** gated by `hub-worlds` — gating "teleport a joining player into the hub" by "is their current world a hub world" would be circular, since the whole point is moving them INTO the hub regardless of where they logged in.

## World Protection (`module: world-protection`)

Defaults **on** only when `server-role: hub`. Every check below only applies in worlds listed in `hub-worlds` (or every world, if empty).

| Check | Config key | Notes |
|---|---|---|
| Block break/place | `deny-block-break` / `deny-block-place` | `swaghub.bypass.build` overrides both. |
| Hunger | `disable-hunger` | |
| Fall damage | `disable-fall-damage` | |
| **All damage (master switch)** | `disable-all-damage` | If `true`, supersedes fall-damage/PvP toggles in practice — no damage of any kind in hub worlds. |
| PvP | `disable-pvp` | `swaghub.bypass.pvp` always allows PvP regardless. |
| PvP zones | `pvp-zones` | Two-corner cuboid exceptions where PvP is allowed even with `disable-pvp: true` — config-only, no wand/selection UI. |
| Weather lock | `lock-weather` / `clear-weather` | |
| Time lock | `lock-time` / `fixed-time` | `0`=dawn, `6000`=noon, `12000`=dusk, `18000`=midnight. |
| Mob spawning | `deny-mob-spawning` | Natural/spawner spawns only — mobs spawned via `SpawnReason.CUSTOM` (e.g. NPC plugins) still get through. |
| Item drop/pickup | `deny-item-drop` / `deny-item-pickup` | |
| Leaf decay / fire spread / block burn | `deny-leaf-decay` / `deny-fire-spread` / `deny-block-burn` | |
| TNT | `deny-tnt` | Cancels `TNTPrimeEvent` — stops TNT from ever priming, covering flint & steel, redstone, fire, and dispenser ignition in one event. |

Weather/time locking is enforced by cancelling the relevant change events plus a one-time force on enable/reload — there's no per-tick busy-loop task keeping it pinned.

## Join Settings (`module: join-settings`)

Defaults **on** only when `server-role: hub`. Everything here applies once the player is in a hub world — including **after** any spawn-on-join teleport has already run (join-settings' listener runs at a later event priority than spawn's, on purpose, so clearing inventory or setting gamemode always evaluates the player's final world).

| Setting | Default | Effect |
|---|---|---|
| `clear-inventory` | `true` | |
| `set-gamemode` / `gamemode` | `true` / `ADVENTURE` | |
| `heal-and-feed` | `true` | |
| `join-firework` | `true` | |
| `first-join.actions` | `[]` | Runs through SwagHub's [action system](join-items.md#the-action-system) once, on a genuinely fresh player profile only — plus a private welcome message from `messages.yml`. |

> **Staff flying in creative and logging off:** `set-gamemode` unconditionally resets gamemode on join by default, which would otherwise force a staff member out of creative flight mid-air. Grant `swaghub.bypass.joingamemode` (see `PlayerStateModule` under [Movement & Extras](movement-and-extras.md#gamemode--flight-persistence)) to restore a player's actual last gamemode and flight state on join instead.

## Related Pages

- [Configuration](../getting-started/configuration.md)
- [Movement & Extras](movement-and-extras.md)
- [Admin Commands](../admin-commands/commands.md)
