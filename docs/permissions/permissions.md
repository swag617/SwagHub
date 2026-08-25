# Permissions

All SwagHub permission nodes, transcribed directly from `plugin.yml`.

## Command Permissions

| Permission | Default | Description |
|---|---|---|
| `swaghub.command.reload` | op | Reload SwagHub's configuration and modules |
| `swaghub.command.info` | true | View SwagHub status info |
| `swaghub.command.setlobby` | op | Set the lobby/spawn location |
| `swaghub.command.lobby` | true | Teleport to the lobby/spawn location |
| `swaghub.command.proxy` | op | Run the `/ah proxy servers` admin diagnostic |
| `swaghub.command.open` | true | Open a SwagHub menu for yourself via `/ah open <menu>` |
| `swaghub.command.open.others` | op | Open a SwagHub menu on behalf of another player |
| `swaghub.command.scoreboard` | true | Toggle your own scoreboard visibility via `/ah scoreboard` |
| `swaghub.command.hologram` | op | Manage holograms via `/ah hologram <...>` |
| `swaghub.command.portal` | op | Manage proxy portals via `/ah portal <...>` |
| `swaghub.command.networkstats` | op | Query another server's player stats via `/ah networkstats <server> <player>` |
| `swaghub.command.fly` | op | Toggle your own flight via `/fly` |
| `swaghub.command.fly.others` | op | Toggle another player's flight via `/fly <player>` |
| `swaghub.command.flyspeed` | op | Set your own flight speed via `/flyspeed <1-10>` |
| `swaghub.command.flyspeed.others` | op | Set another player's flight speed |
| `swaghub.command.gamemode` | op | Change your own game mode via `/gamemode` |
| `swaghub.command.gamemode.others` | op | Change another player's game mode |
| `swaghub.command.vanish` | op | Toggle your own vanish via `/vanish` |
| `swaghub.command.vanish.others` | op | Toggle another player's vanish |
| `swaghub.command.lockchat` | op | Toggle the server-wide chat lock via `/lockchat` |
| `swaghub.command.clearchat` | op | Clear the chat via `/clearchat` |

## Bypass Permissions

| Permission | Default | Description |
|---|---|---|
| `swaghub.bypass.lobbydelay` | op | Skip the `/lobby` teleport delay and move-cancel entirely |
| `swaghub.bypass.build` | op | Bypass the hub's block break/place protection |
| `swaghub.bypass.pvp` | op | Bypass the hub's PvP protection (always allowed to fight) |
| `swaghub.bypass.joingamemode` | op | Restore your last gamemode and flight state on join instead of the hub's forced default |
| `swaghub.bypass.lockchat` | op | Bypass the chat lock and still send messages while it's active |
| `swaghub.bypass.chatcooldown` | op | Bypass the per-message chat cooldown |
| `swaghub.bypass.commandblocker` | op | Bypass the blocked/allowed command list |

## Feature Permissions

| Permission | Default | Description |
|---|---|---|
| `swaghub.doublejump` | op | Use the double jump ability |
| `swaghub.teleportbow` | op | Shoot bows that teleport you to their landing point |
| `swaghub.playerhider.alwaysvisible` | op | Always stay visible to players in the `RANKS_ONLY` player-hider state |
| `swaghub.vanish.see` | op | See players who are currently vanished (passive; not a command permission) |

## Web Editor Permissions

| Permission | Default | Description |
|---|---|---|
| `swaghub.dashboard.view` | op | View SwagHub's hub-options web editor (read-only) |
| `swaghub.dashboard.edit` | op | Save changes via SwagHub's hub-options web editor |

## Admin Wildcard

| Permission | Default | Description |
|---|---|---|
| `swaghub.admin` | op | Full SwagHub admin access (grant via LuckPerms for a wildcard-style parent) |

## Dynamic Permission Families

Two permission families are registered **programmatically at runtime** (one node per admin-defined id, default `true`) rather than being pre-declared in `plugin.yml`, since the ids are entirely open-ended (new items/menus can be added without a plugin update):

| Pattern | Example | Source |
|---|---|---|
| `swaghub.item.<id>` | `swaghub.item.server-selector` | One per entry in `items.yml` |
| `swaghub.menu.<id>` | `swaghub.menu.main-menu` | One per file under `selector-menus/` |

Both are re-registered on every `/ah reload`; a renamed or removed item/menu never leaves a stale permission node behind, since the old id set is unregistered first.

## Default Values

- `true`: every player has this by default; revoke it from groups that shouldn't have access.
- `op`: only server operators have this by default.

## Configuring via a Permission Plugin

```
# Let regular players open the server-selector join item's menu
/lp group default permission set swaghub.item.server-selector true

# Grant a staff group scoreboard-hiding and PvP bypass in the hub
/lp group staff permission set swaghub.bypass.pvp true

# Let a trusted staff group restore their gamemode/flight on relog
/lp group staff permission set swaghub.bypass.joingamemode true
```

## Related Pages

- [Admin Commands](../admin-commands/commands.md)
- [Join Items](../core-features/join-items.md)
- [Server Selector Menus](../core-features/server-selector-menus.md)
