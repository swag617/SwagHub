# Server Selector Menus

`module: menus` — a utility module, always enabled regardless of `server-role`. Every menu is its own YAML file under `selector-menus/` (one file = one menu); there's nothing to configure in `config.yml` itself beyond the Bedrock-forms toggle described below.

## Opening a Menu

- The shipped `server-selector` [join item](join-items.md) (`[open-menu] main-menu`).
- Any `[open-menu] <menu-id>` action, from anywhere the action system runs (another menu's slot, an announcement entry, first-join actions).
- `/ah open <menu> [player]` — requires `swaghub.command.open` (default `true`) for yourself, or `swaghub.command.open.others` (default `op`) to target another player.
- `[close-menu]` closes whatever inventory the acting player currently has open.

## Format

```yaml
title: "<gradient:#7b2ff7:#f107a3><bold>Select a Server</bold></gradient>"
rows: 3
refresh-interval-ticks: 100
open-sound: BLOCK_NOTE_BLOCK_PLING

filler:
  material: GRAY_STAINED_GLASS_PANE
  name: " "

slots:
  11:
    material: GRASS_BLOCK
    name: "<green><bold>Survival</bold></green>"
    lore:
      - "<gray>Status: %swaghub_status_survival%</gray>"
      - "<gray>Players: <white>%swaghub_count_survival%</white></gray>"
    actions:
      - "[server] survival"
```

- **Menu id** — taken from the file's own `id:` key if present, otherwise the filename minus `.yml` (so `main-menu.yml` is menu id `main-menu` either way).
- `title` / `rows` — MiniMessage title, 1–6 rows.
- `filler` — fills every slot not explicitly listed under `slots:` (purely decorative, no actions). Omit for empty/air filler.
- `open-sound` — any `org.bukkit.Sound` name, played to the viewer the instant the menu opens.
- Each slot supports `material`, `name`, `lore`, `custom-model-data`, `glow`, `skull` (same shape as [join items](join-items.md)), and `actions`.

## Live Placeholders

`%swaghub_count_<server>%`, `%swaghub_count_total%`, and `%swaghub_status_<server>%` resolve live counts from the [proxy service](network-player-counts.md) — `<server>` must exactly match a server name configured on your proxy **and** listed under `proxy.servers` in `config.yml`, or the count always reads `0`. Any slot whose name/lore contains one of these tokens is automatically re-rendered every `refresh-interval-ticks` (default `100` ticks / 5s) for as long as at least one player has the menu open. Set `refresh-interval-ticks: 0` to disable auto-refresh. Slots also run through PlaceholderAPI, if installed, for any other plugin's placeholders in the same text.

## Per-Viewer Instances

Every menu viewer gets their **own** `Inventory` instance — menus are never shared across multiple simultaneous viewers, so one player closing their view can never affect another player's still-open session.

## Permissions

Every menu automatically gets its own permission node, `swaghub.menu.<id>` (e.g. `swaghub.menu.main-menu`), registered **programmatically at runtime** with a default of `true` — every player can open every menu by default. There is no separate `permission:` config key; revoke a specific menu the same way you'd revoke a join item, via your permissions plugin.

## Shipped Example: `main-menu.yml`

Ships with three example backend servers (Survival, Skyblock, Factions) each showing live status + player count via `[server] <name>` actions, plus a fourth slot showing the network-wide total with a base64 skull texture.

## Bedrock Note

Chest-GUI menus already work correctly for Bedrock players through Geyser and are the default for everyone. `menus.bedrock-forms` in `config.yml` (default `false`) is a documented, **not-yet-implemented** toggle for a future native Bedrock SimpleForm renderer — enabling it logs one console warning and changes nothing about how menus actually render. See [Bedrock / Floodgate Support](bedrock-floodgate.md).

## Related Pages

- [Join Items](join-items.md) — the action system and shared item-config format
- [Network-Aware Player Counts](network-player-counts.md)
- [Permissions](../permissions/permissions.md)
