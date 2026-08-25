# Join Items

`module: join-items` — defaults **on** only when `server-role: hub`. Fully defined in `items.yml`; there is nothing to configure in `config.yml` itself.

Every entry under `items:` is given to a player as a hotbar item the moment they're in a hub world — on join, on respawn, and when changing **into** a hub world (never when leaving one).

## Format

```yaml
items:
  server-selector:
    material: COMPASS
    slot: 4                # hotbar slot, 0-8 (REQUIRED)
    amount: 1               # stack size, 1-64 (default 1)
    name: "<gradient:#7b2ff7:#f107a3><bold>Server Selector</bold></gradient>"
    lore:
      - "<gray>Right-click to choose a server!</gray>"
    glow: true               # hidden-enchant glow trick, no stat change
    actions:
      - "[open-menu] main-menu"
```

Every field except `material` and `slot` is optional. `custom-model-data` (a legacy int, still supported in 1.21.x) and `skull` (only used with `material: PLAYER_HEAD`, resolved from a base64 texture, `owner-name`, or `owner-uuid`) round out the full format.

A malformed entry (missing/invalid `material`, missing/out-of-range `slot`, etc.) logs one specific warning naming the item id and the problem, and is skipped — every other item still loads and is given normally.

## Identification: PDC Tag Only

Join items are identified **only** by an internal `PersistentDataContainer` tag (`join_item_id`) — never by material or display name. This means a join item can safely share a material or name with anything else in a player's inventory without ambiguity, and it's how SwagHub enforces the item's behavior:

- **Non-movable, non-droppable, unconditionally** — inventory clicks/drags that would move a tagged item, number-key hotbar swaps onto its slot, and drops are all cancelled. This isn't configurable per-item or globally; a "sometimes movable" join item has no coherent use case, since every configured action already assumes the item stays where it was given.
- **Right-click interaction is always cancelled** — a join item is a purely virtual "menu trigger," not a real functioning item. Right-clicking a compass-shaped server selector never opens vanilla's lodestone-tracking UI.

## Permissions

Every item id automatically gets its own permission node, `swaghub.item.<id>` (e.g. `swaghub.item.server-selector`), registered **programmatically at runtime** with a default of `true` — meaning every player has every join item by default. This can't be a free-form `permission:` config key, since item ids are admin-defined; the node is always derived from the id itself. Revoke a specific item for a group/rank via your permissions plugin (e.g. LuckPerms) by negating that one node.

## The Action System

Every configurable "do something" in SwagHub — join items, menu slots, announcements, first-join extras — is a list of strings, each starting with a `[tag]`. An unrecognized tag or malformed line logs one warning naming the problem and is skipped; it never aborts the rest of the sequence.

**Structural tags** (control sequencing/guarding, not a single action):

| Tag | Syntax | Behavior |
|---|---|---|
| `[delay]` | `[delay] <ticks>` | Pauses the rest of the sequence via the Bukkit scheduler — never blocks the calling thread. |
| `[permission-check]` | `[permission-check] <permission>` | If the acting player lacks the permission, every remaining action in the sequence is skipped. |
| `[chance]` | `[chance] <percent>` | Rolls a 0–100 chance; on failure, every remaining action is skipped. |

**Registered action types:**

| Tag | Syntax | Notes |
|---|---|---|
| `[message]` | `[message] <MiniMessage text>` | Chat message to the acting player. |
| `[centered-message]` | `[centered-message] <MiniMessage text>` | Horizontally centered via pixel-width math; colors/gradients survive centering. |
| `[actionbar]` | `[actionbar] <MiniMessage text>` | Action bar message. |
| `[title]` | `[title] title;subtitle;in;stay;out` | Omitting the timing suffix uses Minecraft's defaults (10/70/20 ticks) silently. |
| `[sound]` | `[sound] SOUND_NAME;volume;pitch` | `volume`/`pitch` default to `1.0`. |
| `[player]` | `[player] <command>` | Runs a command as the acting player (no leading `/`). |
| `[console]` | `[console] <command>` | Runs a command as console. |
| `[server]` | `[server] <name>` | Proxy-connects the acting player to a backend server. |
| `[teleport]` | `[teleport] world;x;y;z[;yaw;pitch]` | `yaw`/`pitch` are both-or-neither. |
| `[open-menu]` | `[open-menu] <menu-id>` | Opens a selector menu. See [Server Selector Menus](server-selector-menus.md). |
| `[close-menu]` | `[close-menu]` | Closes whatever inventory the player has open. |
| `[firework]` | `[firework] colors;type;flicker;trail` | `type` — `BALL`/`BALL_LARGE`/`STAR`/`BURST`/`CREEPER`, default `BALL`. |
| `[particle]` | `[particle] Name;count;offsetX;offsetY;offsetZ` | `count` defaults to `10`; offsets default to `0.5`. |
| `[effect]` | `[effect] TypeName;durationTicks;amplifier` | `durationTicks` defaults to `200` (10s); `amplifier` defaults to `0`. |
| `[cycle-player-hider]` | `[cycle-player-hider]` | Advances the player's visibility state — see [Movement & Extras](movement-and-extras.md#player-hider). |

Internal (non-PlaceholderAPI) tokens use `%key%` — matching PlaceholderAPI's own convention. `%player%` is auto-populated from the triggering player unless a caller already supplied that key.

```yaml
# A join item that opens the server selector
actions:
  - "[open-menu] main-menu"

# A gated, delayed welcome sequence (join-settings.first-join.actions)
actions:
  - "[delay] 20"
  - "[message] <gradient:#7b2ff7:#f107a3>Welcome to the network, %player%!</gradient>"
```

## Shipped Example Items

`items.yml` ships with two working examples:

- **`server-selector`** — a compass in slot 4 that opens the `main-menu` server selector.
- **`hider-toggle`** — an ender eye in slot 8 that cycles the player-hider state.

## Bedrock Note

Custom model data only renders for Bedrock/Geyser players when the server has a matching Bedrock resource pack mapped. Every item's material and name/lore still display correctly without one — design items so they look fine without custom model data too. See [Bedrock / Floodgate Support](bedrock-floodgate.md).

## Related Pages

- [Server Selector Menus](server-selector-menus.md)
- [Configuration](../getting-started/configuration.md)
- [Permissions](../permissions/permissions.md)
