# Scoreboard & Tablist

Two modules, `scoreboard` and `tablist`, both default **on** only when `server-role: hub`, and both are only shown to a player while they're standing in a world listed in `config.yml`'s `hub-worlds` (or every world, if that list is empty). Both are among the modules SwagHub auto-yields to SwagCore when it's detected. See [Coexistence with SwagCore](../ecosystem/coexistence.md).

## Scoreboard (`scoreboard.yml`)

```yaml
update-interval-ticks: 20

worlds:
  default:
    title:
      frames:
        - "<gradient:#7b2ff7:#f107a3><bold>SERVER</bold></gradient>"
        - "<gradient:#f107a3:#7b2ff7><bold>SERVER</bold></gradient>"
      frame-interval-ticks: 20
    lines:
      - ""
      - "<gray>Players:</gray> <white>%swaghub_count_total%</white>"
      - ""
```

`worlds` is a map of world name → scoreboard content, plus a mandatory `default` key used as a fallback for any hub world not explicitly listed by name. Each world entry has:

- `title.frames`: one element means a static title; multiple elements cycle every `frame-interval-ticks` (`0` = static, no cycling).
- `lines`: top line first, capped at 15 (Minecraft's real sidebar limit).

Lines and titles resolve through SwagHub's own `%swaghub_...%` placeholders and, if PlaceholderAPI is installed, every other plugin's placeholders too, before being parsed as MiniMessage.

Rendering uses team-based assignment specifically to avoid flicker on update.

### Per-Player Toggle

```
/ah scoreboard
```

Requires `swaghub.command.scoreboard` (default `true`). Toggles the sender's own scoreboard visibility, persisted via SwagAPI's player-data service and applied immediately.

## Tablist (`tablist.yml`)

```yaml
update-interval-ticks: 20

worlds:
  default:
    header:
      frames:
        - "<gradient:#7b2ff7:#f107a3><bold>Welcome!</bold></gradient>"
      frame-interval-ticks: 0
    footer:
      frames:
        - "<gray>Online:</gray> <white>%swaghub_count_total%</white>"
      frame-interval-ticks: 0
```

Same `worlds` map + mandatory `default` fallback shape as the scoreboard, and the exact same frame/interval format for both `header` and `footer`. PlaceholderAPI-aware. **No per-player toggle exists for the tablist.**

## Announcements (`announcements.yml`)

A related, separately-toggled module (`module: announcements`); also defaults on only when `server-role: hub`, gated by `hub-worlds`. Broadcasts to every player currently in an eligible world, not just one triggering player.

```yaml
check-interval-ticks: 20
default-interval-ticks: 600

worlds:
  default:
    rotation: sequential
    entries:
      - actions:
          - "[message] <gradient:#7b2ff7:#f107a3>Thanks for playing on the network!</gradient>"
      - actions:
          - "[actionbar] <yellow>Don't forget to vote for the server!</yellow>"
          - "[sound] ENTITY_EXPERIENCE_ORB_PICKUP;1;1"
```

Each world entry supports `rotation: sequential|random` (default `sequential`) and an optional per-world `interval-ticks` override (falls back to `default-interval-ticks`). Every entry is a list of `actions` using the same [action system](join-items.md#the-action-system) as join items and menus: any combination of `[message]`, `[actionbar]`, `[title]`, `[sound]`, `[centered-message]`, or any other registered action type.

## Related Pages

- [Network-Aware Player Counts](network-player-counts.md): where `%swaghub_count_total%` actually comes from
- [Coexistence with SwagCore](../ecosystem/coexistence.md)
- [Configuration](../getting-started/configuration.md)
