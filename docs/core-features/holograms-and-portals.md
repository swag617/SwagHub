# Holograms & Portals

Two independent, always-on utility modules — neither depends on `server-role`, and neither is gated by `hub-worlds` (both are admin-**placed** at specific chosen locations, not world-wide behaviors).

## Holograms (`module: holograms`, `holograms.yml`)

Always enabled regardless of `server-role`. Compat-reserved — yields to SwagCore's own hologram system when SwagCore is detected (see [Coexistence with SwagCore](../ecosystem/coexistence.md)).

Every hologram is native `TextDisplay`-entity based — **one entity per line**, stacked vertically below the hologram's anchor point, top line first — never one multi-line entity.

```yaml
refresh-interval-ticks: 100
line-spacing: 0.28

holograms:
  example:
    world: "world"
    x: 0.5
    y: 65.0
    z: 0.5
    lines:
      - "<gradient:#7b2ff7:#f107a3><bold>Welcome to the hub!</bold></gradient>"
      - "<gray>Manage holograms with </gray><yellow>/ah hologram</yellow>"
      - "<gray>Players online: <white>%swaghub_count_total%</white></gray>"
    refresh-interval-ticks: 100   # optional per-hologram override
```

`holograms.yml` — not any world-saved entity state — is always the source of truth: on every module enable and every `/ah reload`, SwagHub re-derives the correct entity set from this file, **patching** (never blindly recreating) whatever it finds already in the world. Every entity is PDC-tagged, so orphan cleanup on startup never touches another plugin's `TextDisplay`s — including SwagCore's own hologram system, if both happen to be running side by side.

Lines are parsed as MiniMessage and support `%swaghub_...%` placeholders plus any PlaceholderAPI placeholder, resolved fresh on every render tick. Since a shared world hologram has no single "acting player," player-specific placeholders that need one resolve as if for a null player.

### Managing Holograms

```
/ah hologram create <id>
/ah hologram delete <id>
/ah hologram addline <id> <text...>
/ah hologram setline <id> <index> <text...>
/ah hologram removeline <id> <index>
/ah hologram movehere <id>
/ah hologram list
```

Requires `swaghub.command.hologram` (default `op`) — a single node gates the whole subtree. `create`/`movehere` need a real location and are player-only; the rest are console-usable. Every command writes `holograms.yml` to disk **immediately**, never only on shutdown; you can also hand-edit the file directly and run `/ah reload`.

## Proxy Portals (`module: portals`, `portals.yml`)

Always enabled regardless of `server-role`, and **never** auto-yielded to any plugin — portals aren't a "hub visual identity" feature the way scoreboard/tablist/holograms are.

A portal is a config-defined cuboid that proxy-connects any player who walks into it to a configured backend server. Portals are **not** action-list-driven — walking into one calls the [proxy service](network-player-counts.md) directly, the same way `/setlobby`'s location is a real admin-placed spot rather than a world-wide toggle.

```yaml
cooldown-seconds: 3

portals: {}
  # example:
  #   world: "world"
  #   server: "survival"
  #   corner1: { x: 100, y: 60, z: 100 }
  #   corner2: { x: 103, y: 63, z: 103 }
  #   cooldown-seconds: 5   # optional per-portal override
```

`cooldown-seconds` is tracked **globally per player, not per-portal** — this is what actually prevents a reconnect/bounce-back loop through *any* portal, not just the same one twice in a row. A blocked re-trigger during the cooldown window is always silent (no message), to avoid spamming a player standing inside a portal's cuboid.

No example portal ships by default — unlike holograms, a portal needs a real destination backend server name (which the plugin can't safely guess) and a region an admin has actually selected.

### Creating a Portal

```
/ah portal wand
```

Requires `swaghub.command.portal` (default `op`). Gives a tagged stick — it never breaks or interacts with the world.

1. Left-click a block for corner 1, right-click a block for corner 2.
2. `/ah portal create <id> <server>` — `<server>` must exactly match a backend name configured on the proxy itself (same requirement as `proxy.servers` in `config.yml`).

```
/ah portal delete <id>
/ah portal list
```

Every command writes `portals.yml` to disk immediately; you can also hand-edit it and run `/ah reload`.

## Related Pages

- [Network-Aware Player Counts](network-player-counts.md)
- [Coexistence with SwagCore](../ecosystem/coexistence.md)
- [Admin Commands](../admin-commands/commands.md)
