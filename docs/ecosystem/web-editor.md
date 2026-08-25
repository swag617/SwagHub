# Web Editor

When SwagAPI's shared web service is running, SwagHub's hub-options editor is served at `/swagapi/swaghub/` — no separate port, no separate login. SwagHub never runs its own HTTP server or authentication; it registers into SwagAPI's existing one.

`module: webeditor` — always enabled regardless of `server-role`, a utility module like proxy/menus/holograms/portals. **Never** auto-yielded to any plugin — nothing else in the Swag ecosystem exposes this specific plugin's own config over HTTP.

## Access

- `swaghub.dashboard.view` (default `op`) — read-only access to the status page and editors.
- `swaghub.dashboard.edit` (default `op`) — required to save changes through any editor.

## What's Covered

Five JSON endpoints under `/swagapi/swaghub/api/`:

- A read-only **status page** — version, server role, every module's enabled/override/yielded state, proxy status, pending-update info.
- Read/write editors for **core options** (server role, hub worlds, per-module enable/disable, compatibility overrides).
- Read/write editors for **scoreboard**, **tablist**, and **announcements**.

Modules currently yielded to SwagCore are reported as **read-only** in the editor, with the yield reason shown ("Managed by SwagCore") — the UI greys them out rather than letting you edit a file nothing is actually reading.

## What's Not Covered Yet

**Holograms, portals, items, and menus do not have a web editor yet.** Manage those in-game (`/ah hologram`, `/ah portal`) or by hand-editing their YAML files (`holograms.yml`, `portals.yml`, `items.yml`, `selector-menus/*.yml`) and running `/ah reload`.

## Known Limitation: Offline Permission Checks

Permission checks for the web editor can only be resolved against a currently **online** player — `OfflinePlayer` doesn't expose permission lookups, and SwagHub deliberately takes no Vault dependency the way SwagCore does for this. A staff member granted `swaghub.dashboard.edit` by a permissions plugin is recognized only while they're online in-game; the same account browsing the panel while offline falls back to `isOp()`. See [Known Limitations](../troubleshooting/known-limitations.md).

## Related Pages

- [Coexistence with SwagCore](coexistence.md)
- [Scoreboard & Tablist](../core-features/scoreboard-tablist.md)
- [Permissions](../permissions/permissions.md)
