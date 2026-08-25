# ⚙️ Configuration

`config.yml` is the core configuration file, generated in `plugins/SwagHub/` on first startup. Run `/ah reload` after editing it — every section documented here is picked up live.

> SwagHub splits its configuration across several files, not just `config.yml`: **core settings, world protection, spawn, proxy, and every module without its own visual content** live in `config.yml`. Everything with real per-item/per-entry content gets its own file — `items.yml` (join items), `scoreboard.yml`, `tablist.yml`, `announcements.yml`, `launchpads.yml`, `holograms.yml`, `portals.yml`, and one file per menu under `selector-menus/`. Each is covered on its own feature page, linked below.

## Server Role

```yaml
server-role: hub
```

`hub` — this server is the network's hub/lobby; every module follows its own individual toggle (or its coded default) below. `game` — this is a "main"/game server SwagCore (or similar) already runs full-fat on: hub-behavior modules (world protections, forced spawn-on-join, join items, clear-inventory, double jump, teleport bow, player hider, anti-WDL, chat lock/cooldown, clearchat, scoreboard, tablist, announcements, fly/gamemode/vanish commands) default **off**. Utility modules unique to SwagHub (holograms, custom menus, proxy service, portals, launchpads, the web editor, network stats, player-state) default **on** either way. Server role only changes defaults — it never hard-locks anything.

## Per-Module Toggles

```yaml
modules: {}
```

Format: `modules.<module-key>: true/false`. If a key is omitted, the module's own coded default is used (which may itself depend on `server-role` above). Ships **empty on purpose** — explicitly writing a module's key here (even to `true`) always wins over the role-based default, so leaving it absent is what lets `server-role: game` actually flip a module off without you having to remember to un-set it.

## Compatibility / Auto-Yield

```yaml
compatibility:
  auto-yield: true
  overrides:
    # scoreboard: enabled
    # tablist: enabled
    # announcements: enabled
    # join-quit-messages: enabled
  conflicts: {}
```

`auto-yield` automatically disables ("yields") any SwagHub module that overlaps with a detected plugin from the built-in conflict registry (SwagCore → `scoreboard`/`tablist`/`announcements`/`join-quit-messages`/`holograms`/`vanish`; EssentialsX → `fly`/`gamemode`/`vanish`/`clearchat`). `overrides.<module>: auto | enabled | disabled` takes priority over auto-yield in both directions. `conflicts` lets you **extend** the registry — entries here are merged with (never replace) the built-in defaults. Full detail on [Coexistence with SwagCore](../ecosystem/coexistence.md).

## Hub World Whitelist

```yaml
hub-worlds: []
```

Every hub behavior (protections, join settings, scoreboard, tablist, announcements) is enforced only in the worlds listed here. Leave empty to apply to **all** worlds.

## Bedrock Menu Rendering

```yaml
menus:
  bedrock-forms: false
```

Chest-GUI selector menus already work correctly for Bedrock players through Geyser — this is off by default on purpose. Setting it to `true` requests native Bedrock SimpleForm rendering, which **is not implemented yet**: it logs one console warning on enable/reload and changes nothing about how menus actually render. See [Bedrock / Floodgate Support](../core-features/bedrock-floodgate.md).

## Spawn / Lobby

```yaml
spawn:
  lobby-teleport-delay-ticks: 60
  cancel-on-move: true
  spawn-on-join: true
  spawn-on-void-fall: true
  spawn-on-respawn: true
```

Always enabled regardless of `server-role`. Only **one global lobby location** is supported (no per-world lobbies) — set it with `/setlobby` while standing where you want players to land; it's written to `data/spawn.yml` **immediately**, never only on shutdown. Full detail on [Hub Essentials](../core-features/hub-essentials.md).

## World Protection

```yaml
world-protection:
  deny-block-break: true
  deny-block-place: true
  disable-hunger: true
  disable-fall-damage: true
  disable-all-damage: false
  disable-pvp: true
  pvp-zones: []
  lock-weather: true
  clear-weather: true
  lock-time: true
  fixed-time: 6000
  deny-mob-spawning: true
  deny-item-drop: true
  deny-item-pickup: true
  deny-leaf-decay: true
  deny-fire-spread: true
  deny-block-burn: true
  deny-tnt: true
```

Defaults **on** only when `server-role: hub`. `disable-all-damage` is a master switch that supersedes `disable-fall-damage`/`disable-pvp` in practice. `pvp-zones` are simple two-corner cuboids (config-only, no wand) where PvP is allowed even while `disable-pvp: true` elsewhere in the world. Full detail on [Hub Essentials](../core-features/hub-essentials.md).

## Join Settings

```yaml
join-settings:
  clear-inventory: true
  set-gamemode: true
  gamemode: ADVENTURE
  heal-and-feed: true
  join-firework: true
  first-join:
    actions: []
```

Defaults on only when `server-role: hub`. Everything here applies once the player is actually in a hub world — including after any spawn-on-join teleport. `first-join.actions` runs through the same [action system](../core-features/join-items.md#the-action-system) as join items and menus.

## Proxy Service

```yaml
proxy:
  poll-interval-seconds: 10
  servers: []
  connect-timeout-ticks: 40
```

Utility module — always enabled regardless of `server-role`; toggle the whole module via `modules.proxy: true/false`. `servers` must exactly match the backend server names configured on the proxy itself. Full detail on [Network-Aware Player Counts](../core-features/network-player-counts.md).

## Join Items & Menus

Neither has settings inside `config.yml` — join items live entirely in `items.yml`, and each server-selector menu is its own file under `selector-menus/`. See [Join Items](../core-features/join-items.md) and [Server Selector Menus](../core-features/server-selector-menus.md).

## Double Jump

```yaml
double-jump:
  power: 1.4
  height: 1.2
  particle: CLOUD
  sound: ENTITY_BAT_TAKEOFF
  bedrock: true
  regions: []
```

Defaults on only when `server-role: hub`. `bedrock: false` disables double jump specifically for Bedrock players if double-tap-space input ever proves unreliable through Geyser — Java players are never affected either way. `regions` are two-corner cuboids where only the *launch effect* is disabled (the underlying flight grant is untouched). Full detail on [Movement & Extras](../core-features/movement-and-extras.md).

## Teleport Bow / Player Hider

```yaml
player-hider:
  cooldown-seconds: 3
```

Teleport bow has no settings beyond enable/disable + its permission — any bow shot by a permitted player in a hub world becomes a one-shot teleport arrow. Player hider is cycled via the `[cycle-player-hider]` action (see `items.yml`'s shipped `hider-toggle` example) and only has a per-player cycle cooldown. Both default on only when `server-role: hub`. Full detail on [Movement & Extras](../core-features/movement-and-extras.md).

## Fly / Gamemode / Vanish

No settings beyond enable/disable and their permissions — all three default on only when `server-role: hub`, and all three are compat-reserved (yield to SwagCore's/EssentialsX's own equivalents). See [Chat & Utility Commands](../core-features/chat-and-utility-commands.md).

## Chat Controls (lockchat)

```yaml
lockchat:
  cooldown-seconds: 0
  command-blocker:
    mode: blacklist
    commands: []
```

Bundles `/lockchat`, a per-player chat cooldown, and a command whitelist/blacklist into one module. `locked` is pure runtime state — never persisted, always starting unlocked on enable/reload. Defaults on only when `server-role: hub`.

## Clear Chat

```yaml
clearchat:
  lines: 100
  clear-for-everyone: true
```

A **separate** module from `lockchat`, since only `clearchat` yields to EssentialsX. Defaults on only when `server-role: hub`.

## Anti-WorldDownloader

```yaml
anti-wdl:
  action: kick
```

Detects the WorldDownloader plugin-messaging channel and either `kick`s or `warn`s — never both. Defaults on only when `server-role: hub`.

## Metrics

```yaml
metrics:
  enabled: true
```

Sends anonymous usage statistics (server count, online player count, software/version — never player names, IPs, or anything server-specific) to [bStats](https://bstats.org) via a shaded/relocated client. Has no `modules:` entry — bStats' own `Metrics` class self-manages its lifecycle once constructed. Set to `false` to opt out entirely.

> As shipped, SwagHub's bStats plugin id is still the placeholder value `0` — with a placeholder id, metrics are simply **never sent at all** (one info-level log line explains why), rather than being sent unattributed. See [Known Limitations](../troubleshooting/known-limitations.md).

## Network Stats

```yaml
network:
  shared-secret: ""
  known-servers: {}
    # smp: "http://10.0.0.180:8080/swagnet/swagcore/"
    # factions: "http://10.0.0.181:8080/swagnet/swagcore/"
```

Lets the hub read player stats (balance, rank, playtime, homes) from other servers on your network over HTTP via each one's SwagCore — **without** connecting directly to their databases, keeping each game type's data physically isolated. Always enabled; harmless if left unconfigured (every lookup just returns empty). `shared-secret` must match the target server's own SwagAPI `network.shared-secret` exactly. Test it with `/ah networkstats <server> <player>`. See [Network-Aware Player Counts](../core-features/network-player-counts.md#network-stats-cross-server-player-lookups) for the full mechanism, including the evacuation/auto-return flow.

## What Requires a Restart

Nothing, in practice — every section above is picked up by `/ah reload`. The one exception is a module that fails to detect a soft-dependency (Floodgate, PlaceholderAPI, LuckPerms) at all — those are only probed once, during `onEnable()`, so installing one fresh requires a restart (not just a reload) to be picked up.

## Related Pages

- [Installation](installation.md)
- [Admin Commands](../admin-commands/commands.md)
- [Permissions](../permissions/permissions.md)
