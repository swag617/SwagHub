# Installation

## Requirements

- **Paper 1.21.x** — the only supported platform. No Spigot, no 1.20.x backport, no Folia (yet). `pom.xml` compiles against `paper-api 1.21.1-R0.1-SNAPSHOT` and `plugin.yml` declares `api-version: '1.21'`.
- **Java 21**.
- **[SwagAPI](https://github.com/) 1.0.0 — hard dependency.** SwagHub declares `depend: [SwagAPI]` and will not enable without it. Bukkit enforces the load order automatically; SwagHub also double-checks at runtime that SwagAPI actually finished enabling, in case it's present but failed its own startup. SwagHub owns no database driver, connection pool, or player-data store of its own — all of that is SwagAPI's job.
- **Optional:** PlaceholderAPI, LuckPerms, Floodgate — soft dependencies (`softdepend: [floodgate, PlaceholderAPI, LuckPerms]`). SwagHub degrades gracefully with all three absent. See [Bedrock / Floodgate Support](../core-features/bedrock-floodgate.md) for what Floodgate's presence actually enables.
- **Optional, for coexistence:** SwagCore, EssentialsX — SwagHub actively detects both and yields overlapping features automatically. See [Coexistence with SwagCore](../ecosystem/coexistence.md).

## Single Server (No Proxy)

1. Install `SwagAPI.jar` in `plugins/` — SwagHub will not enable without it.
2. Install `SwagHub.jar` in `plugins/`.
3. Start the server once to generate SwagHub's default config files.
4. Leave `proxy.enabled` (`modules.proxy` in `config.yml`) alone, or set it to `false` — proxy features have no effect with no proxy in front of the server, but leaving them on is harmless: poll cycles are skipped silently whenever no `Connect` channel exists to send them through.
5. Stand where you want players to land and run `/setlobby`.

## BungeeCord Network

1. Install `SwagAPI.jar` + `SwagHub.jar` on every backend Paper server that should run SwagHub — typically just the hub server (see [Coexistence with SwagCore](../ecosystem/coexistence.md) for the recommended multi-server topology).
2. **No proxy-side plugin is required.** SwagHub's proxy service talks to BungeeCord entirely over the plugin-messaging channel `bungeecord:main` (registered under its legacy name `"BungeeCord"`) — the same channel BungeeCord has always used.
3. In `config.yml`'s `proxy:` section, list the exact backend server names configured in BungeeCord's own `config.yml` under `servers:` so live player counts resolve.
4. That's it — `Connect`/`PlayerCount`/`GetServers` all work immediately.

## Velocity Network

1. Same two-jar install as BungeeCord, on every backend server.
2. **Still no proxy-side plugin required.** Velocity answers the `bungeecord:main` channel natively via its built-in BungeeCord-compatibility layer, so the exact same implementation works against both proxies unmodified.
3. **One thing to verify on Velocity specifically:** `bungee-plugin-message-channel` must be `true` in `velocity.toml`. This is Velocity's own **default** — a stock install needs no changes — but if you've deliberately turned it off, SwagHub's proxy features have nothing to talk to until it's re-enabled.
4. List backend server names under `proxy.servers` in `config.yml`, matching the `[servers]` block in `velocity.toml`, exactly as with BungeeCord.

> See [Network-Aware Player Counts](../core-features/network-player-counts.md) for exactly how the live counts and server-status placeholders work once this is set up.

## Building From Source

```bash
mvn clean package
```

Produces `target/SwagHub-1.0.0-shaded.jar` (the shade plugin replaces the plain jar with this relocated/shaded artifact — bStats is the only currently-shaded dependency). Compiling requires two reference jars under `libs/`:

- `libs/SwagAPI-1.0.0.jar` — compiled against as a `system`-scope dependency; a hard runtime requirement.
- `libs/SwagCore-1.0.0.jar` — kept only as a reference copy for manual coexistence testing; never imported or shaded, since SwagCore is detected at runtime by plugin name only.

> **Note:** the `libs/` folder is dev-time only. A real server deployment needs `SwagAPI.jar` (and, for coexistence, `SwagCore.jar`) placed directly in that server's own `plugins/` folder — the `libs/` path inside this repository is never read by a running server.

## Default Permissions — Read This First

Most player-facing SwagHub permissions default to `true` (join items, `/lobby`, opening menus, the scoreboard toggle), but every **admin** action — `/setlobby`, `/ah reload`, `/ah hologram`, `/ah portal`, `/fly`, `/gamemode`, `/vanish`, and more — defaults to **op**. See [Permissions](../permissions/permissions.md) for the full, exact list transcribed from `plugin.yml`.

## Next Steps

- [Configuration](configuration.md) — a tour of `config.yml`'s sections and every module's defaults
- [Hub Essentials](../core-features/hub-essentials.md) — spawn/lobby, world protection, and join settings
- [Admin Commands](../admin-commands/commands.md) — the full command reference
