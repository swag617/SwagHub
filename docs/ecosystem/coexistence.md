# Coexistence with SwagCore

SwagHub is designed to run alongside **SwagCore** (the Swag ecosystem's Essentials/CMI-style plugin) on the same network, and sometimes the same server, without fighting it for any feature. Two mechanisms make this automatic.

## Server Role

```yaml
server-role: hub
```

`hub`: this server is the network's hub/lobby; every module follows its own individual toggle in `config.yml` (or its coded default). `game`: this is a "main"/game server that SwagCore (or similar) already runs full-fat on. On `game` role, hub-behavior modules default **off**:

- World protection, forced spawn-on-join, join items, clear-inventory
- Double jump, teleport bow, player hider, anti-WDL
- Chat lock/cooldown, clearchat
- Scoreboard, tablist, announcements
- `/fly`, `/gamemode`, `/vanish`

Utility modules unique to SwagHub stay **on** regardless of role: holograms, custom menus, the proxy service, portals, launchpads, the web editor, network stats, and player-state (gamemode/flight persistence). Every default can still be overridden individually; server role only changes defaults, it never hard-locks anything.

## Auto-Yield

```yaml
compatibility:
  auto-yield: true
  conflicts: {}
```

SwagHub detects SwagCore and EssentialsX at startup by plugin name and automatically disables ("yields") the modules each one already owns, logging exactly one console line per yielded module. The shipped registry:

| Detected plugin | SwagHub modules yielded |
|---|---|
| **SwagCore** | `scoreboard`, `tablist`, `announcements`, `join-quit-messages`, `holograms`, `vanish` |
| **EssentialsX** | `fly`, `gamemode`, `vanish`, `clearchat` |

Extend the registry with `compatibility.conflicts.<PluginName>: [modules...]` in `config.yml`. Entries there are **merged (unioned)** with the built-in defaults, never replacing them, so a config typo or partial redeclaration can only ever add extra yielded modules, never accidentally un-map a built-in one.

> Module-enabling (and the auto-yield decision) is deliberately deferred to the first server tick after `onEnable()`, not run synchronously inside it. Bukkit's plugin load order between SwagHub and SwagCore isn't guaranteed by either plugin's `plugin.yml`, so running the yield check too early could see zero yield lines at boot if SwagCore hadn't finished enabling yet.

## Per-Module Overrides

```yaml
compatibility:
  overrides:
    # scoreboard: auto
    # tablist: auto
    # announcements: auto
    # join-quit-messages: auto
    # holograms: auto
    # vanish: auto
    # fly: auto
    # gamemode: auto
    # clearchat: auto
```

`compatibility.overrides.<module>: auto | enabled | disabled` (default `auto`) takes priority over auto-yield in both directions:

- `auto`: follow auto-yield logic (yield if a conflicting plugin is detected, otherwise run per the module's own toggle).
- `enabled`: force this module on even if a conflicting plugin is present.
- `disabled`: force this module off regardless of conflicts.

### Recommended Hub-Server Setup

When SwagCore is *also* installed on the hub server (the standard topology: SwagCore typically runs on every backend including the hub), `config.yml` ships this block pre-written and commented out:

```yaml
compatibility:
  overrides:
    scoreboard: enabled        # hub-specific scoreboard
    tablist: enabled           # hub-specific tablist
    announcements: enabled     # hub-specific broadcasts
    join-quit-messages: enabled # hub-specific join/quit experience
    # vanish: auto      -> stays yielded to SwagCore (DB-persisted, network-wide)
    # holograms: auto   -> stays yielded to SwagCore (one hologram system network-wide)
```

Uncomment this block on the hub server only: SwagHub owns the hub's visual identity there, while SwagCore keeps shared, network-wide systems like vanish and holograms. On pure game servers, leave everything at `auto` with `server-role: game`; no overrides needed.

## Diagnosing a Misconfiguration

```
/ah info
```

Requires `swaghub.command.info` (default `true`). Shows the current server role, every module's registered/enabled state, every yielded module and which plugin it yielded to, and any active overrides: the single place to check when a feature isn't behaving as expected on a mixed SwagCore/SwagHub install.

## Related Pages

- [Configuration](../getting-started/configuration.md)
- [Scoreboard & Tablist](../core-features/scoreboard-tablist.md)
- [Admin Commands](../admin-commands/commands.md)
