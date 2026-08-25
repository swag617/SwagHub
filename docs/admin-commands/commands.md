# Admin Commands

SwagHub registers nine top-level commands. The root command is `/swaghub`, aliased to `/ah` (all examples below use `/ah`). Running `/ah` with no arguments is equivalent to `/ah info`.

## `/ah` (`/swaghub`)

```
/ah reload
/ah info
/ah proxy servers
/ah open <menu> [player]
/ah scoreboard
/ah hologram <create|delete|addline|setline|removeline|movehere|list>
/ah portal <wand|create|delete|list>
/ah networkstats <server> <player>
```

| Subcommand | Permission | Notes |
|---|---|---|
| `reload` | `swaghub.command.reload` (op) | Reloads `config.yml`, `messages.yml`, the compatibility registry, and re-evaluates every module's enabled state. |
| `info` | `swaghub.command.info` (true) | Prints version, server role, module count, and every yielded module + which plugin it yielded to. Also shows a pending-update line if one exists. |
| `proxy servers` | `swaghub.command.proxy` (op) | Fires a `GetServers` query. See [Network-Aware Player Counts](../core-features/network-player-counts.md). |
| `open <menu> [player]` | `swaghub.command.open` (true) / `.open.others` (op) | Opens a selector menu for yourself, or for `[player]` with the `.others` permission. |
| `scoreboard` | `swaghub.command.scoreboard` (true) | Toggles your own scoreboard visibility. Player-only. |
| `hologram <...>` | `swaghub.command.hologram` (op) | One permission gates the whole subtree. See [Holograms & Portals](../core-features/holograms-and-portals.md). |
| `portal <...>` | `swaghub.command.portal` (op) | One permission gates the whole subtree. See [Holograms & Portals](../core-features/holograms-and-portals.md). |
| `networkstats <server> <player>` | `swaghub.command.networkstats` (op) | Queries another server's SwagCore for a player's balance/rank/playtime/homes over HTTP. See [Network-Aware Player Counts](../core-features/network-player-counts.md#network-stats-cross-server-player-lookups). |

An unrecognized subcommand replies with an `unknown-subcommand` message rather than silently doing nothing.

## `/setlobby`

```
/setlobby
```

Requires `swaghub.command.setlobby` (op). Sets the lobby location to your current position, written to `data/spawn.yml` immediately. See [Hub Essentials](../core-features/hub-essentials.md).

## `/lobby`

```
/lobby
```

Requires `swaghub.command.lobby` (true). Teleports you to the lobby, subject to `spawn.lobby-teleport-delay-ticks` and `spawn.cancel-on-move`. `swaghub.bypass.lobbydelay` (op) skips both.

## `/fly [player]`

```
/fly
/fly <player>
```

`swaghub.command.fly` (self, op) / `.fly.others` (op, to target `<player>`). Toggles flight. Compat-reserved: yields to SwagCore/EssentialsX when detected. See [Chat & Utility Commands](../core-features/chat-and-utility-commands.md).

## `/flyspeed <1-10> [player]`

```
/flyspeed <1-10>
/flyspeed <1-10> <player>
```

`swaghub.command.flyspeed` (self, op) / `.flyspeed.others` (op). The speed argument always occupies the first slot, whether or not a target follows it. See [Movement & Extras](../core-features/movement-and-extras.md#gamemode--flight-persistence).

## `/gamemode <mode> [player]`

```
/gamemode <survival|creative|adventure|spectator> [player]
```

Aliases: `/gmc` `/gms` `/gma` `/gmsp` (each infers its own target mode). `swaghub.command.gamemode` (self, op) / `.gamemode.others` (op). Accepts full names or vanilla's short forms (`s`/`c`/`a`/`sp`), case-insensitive.

## `/vanish [player]`

```
/vanish
/vanish <player>
```

`swaghub.command.vanish` (self, op) / `.vanish.others` (op). `swaghub.vanish.see` (op) lets a viewer see vanished players. It's a passive permission, not required to run the command itself.

## `/lockchat`

```
/lockchat
```

`swaghub.command.lockchat` (op). Toggles the server-wide chat lock. `swaghub.bypass.lockchat` (op) always bypasses it.

## `/clearchat`

```
/clearchat
```

`swaghub.command.clearchat` (op). Sends `clearchat.lines` (default 100) blank lines, to everyone or just the sender per `clearchat.clear-for-everyone`.

## Tab Completion

| Command / Position | Suggestions |
|---|---|
| `/ah` arg 1 | `reload`, `info`, `proxy`, `open`, `scoreboard`, `hologram`, `portal`, `networkstats` |
| `/ah proxy` arg 2 | `servers` |
| `/ah open` arg 2 | Loaded menu ids |
| `/ah open <menu>` arg 3 | Online player names |
| `/ah hologram` arg 2 | `create`, `delete`, `addline`, `setline`, `removeline`, `movehere`, `list` |
| `/ah hologram <mutating sub>` arg 3 | Existing hologram ids |
| `/ah portal` arg 2 | `wand`, `create`, `delete`, `list` |
| `/ah portal delete` arg 3 | Existing portal ids |
| `/ah networkstats` arg 2 | Known server ids (`network.known-servers` keys) |
| `/ah networkstats <server>` arg 3 | Online player names |
| `/gamemode` arg 1 | `survival`, `creative`, `adventure`, `spectator` |

## See Also

- [Permissions](../permissions/permissions.md)
- [Hub Essentials](../core-features/hub-essentials.md)
- [Network-Aware Player Counts](../core-features/network-player-counts.md)
