# Network-Aware Player Counts

SwagHub shows live, network-wide player counts on the scoreboard, tablist, and server-selector menus without any proxy-side plugin and without a shared database. This page explains the actual mechanism, the plugin-messaging proxy bridge, plus the separate cross-server stats/evacuation system that queries other servers over HTTP instead.

## How It Works: `bungeecord:main`

`module: proxy`: a utility module, always enabled regardless of `server-role`; toggle the whole thing via `modules.proxy: true/false`.

All proxy communication rides the plugin-messaging channel **`bungeecord:main`**, registered under its legacy name `"BungeeCord"`. This is the same channel BungeeCord has always used. **Velocity answers this channel natively** via its built-in BungeeCord-compatibility layer, so the identical implementation works against both proxies with **zero proxy-side plugin required**. (Velocity's side of this is controlled by `bungee-plugin-message-channel` in `velocity.toml`, which defaults to `true`; a stock install needs no changes.)

SwagHub implements five sub-channels over this pipe:

| Sub-channel | Used for |
|---|---|
| `Connect` | Sends the acting player to a named backend server (the `[server] <name>` action, portals, `ConnectOther`-driven auto-return). |
| `ConnectOther` | Sends a *different*, named player to a backend server; used by `NetworkStatsModule`'s auto-return flow. |
| `PlayerCount` | Polled per configured server, populates the live count cache. |
| `PlayerList` | Cached per-server player-name lists. |
| `GetServers` | The proxy-wide server list, used by `/ah proxy servers`. |

### Polling

```yaml
proxy:
  poll-interval-seconds: 10
  servers: []
  connect-timeout-ticks: 40
```

- `servers`: backend names to poll individual counts for. These must **exactly match** the names configured on the proxy itself (Velocity's `[servers]` block, or BungeeCord's `listeners/servers` section); the proxy has no way to answer `PlayerCount` for a name it doesn't recognize.
- The **network-wide total** is always polled too, regardless of this list.
- Plugin messages can only be sent through a currently-connected player's channel. If zero players are online on this server when a poll cycle would run, that cycle is **skipped silently** (not logged as an error, not zeroed out) and the last-known values keep being served until someone rejoins.
- `connect-timeout-ticks`: how long to wait after a `Connect` request before concluding it silently failed (the proxy protocol has no explicit failure acknowledgement on this channel; the only observable symptom is the player still being present on this server after the timeout). When that happens, the configured `server-offline` message fires automatically.

## Placeholders

Every `%swaghub_...%` token resolves internally, so no PlaceholderAPI installation is required to use them inside SwagHub's own menus/scoreboard/tablist/announcements/holograms. PlaceholderAPI is only needed to pull in *other* plugins' placeholders alongside them.

| Token | Resolves to |
|---|---|
| `%swaghub_count_total%` | Cached total online player count across the whole network. |
| `%swaghub_count_<server>%` | Cached player count for one backend server (`0` if never polled). |
| `%swaghub_status_<server>%` | `<green>Online</green>` / `<red>Offline</red>`: "online" means at least one `PlayerCount` response has been received for that server name since this server started. |

These four are what power the shipped `scoreboard.yml`/`tablist.yml`/`selector-menus/main-menu.yml` defaults out of the box. See [Scoreboard & Tablist](scoreboard-tablist.md) and [Server Selector Menus](server-selector-menus.md).

## Admin Diagnostic

```
/ah proxy servers
```

Requires `swaghub.command.proxy` (default `op`). Since a `GetServers` query is inherently asynchronous, this sends an immediate "querying" acknowledgement, then a follow-up message listing every proxy-reported server (including ones not in your local `proxy.servers` list, since `GetServers` is proxy-wide) with its last-known count, once the response arrives.

## Network Stats: Cross-Server Player Lookups

A **separate, unrelated** feature: `module: networkstats`, always enabled, HTTP-based rather than plugin-messaging-based. Where the proxy service above answers "how many players are online," `NetworkStatsModule` answers "what does this specific player's data look like on another server" (balance, rank, playtime, and home count) by calling that server's own SwagCore instance over HTTP, **never** connecting to its database directly. This keeps each game type's data physically isolated (an SMP server's economy never touches a Factions server's) while still letting the hub display both.

```yaml
network:
  shared-secret: ""
  known-servers: {}
    # smp: "http://10.0.0.180:8080/swagnet/swagcore/"
    # factions: "http://10.0.0.181:8080/swagnet/swagcore/"
```

- `shared-secret` must match the target server's own SwagAPI `network.shared-secret` exactly (sent as the `X-SwagNetwork-Key` header). Leave blank to disable all outgoing requests.
- `known-servers` maps a short server id to the full URL of that server's SwagCore network-service mount.
- Every fetch has a 3-second timeout and is cached for 60 seconds; an unreachable, unconfigured, or slow server just yields no data and never blocks or throws.

```
/ah networkstats <server> <player>
```

Requires `swaghub.command.networkstats` (default `op`). Same asynchronous two-message shape as `/ah proxy servers`: an immediate "querying" line, then balance/rank/playtime/homes once the HTTP round trip completes.

### Evacuation Auto-Return

`NetworkServiceApiHandler` also exposes one inbound route, `POST /evacuated` (mounted under SwagAPI's shared web service at `/swagnet/swaghub/`), for a game server to report players it just sent to the hub ahead of its own restart. SwagHub queues each reported player and polls, every 5 seconds, whether that origin server has come back up (a plain reachability GET against its configured `known-servers` URL; any non-5xx response counts). Once healthy, every queued player is automatically sent back via `ConnectOther`. Queue entries older than 30 minutes are dropped regardless, so a server that never comes back doesn't poll forever.

## Related Pages

- [Scoreboard & Tablist](scoreboard-tablist.md)
- [Server Selector Menus](server-selector-menus.md)
- [Installation](../getting-started/installation.md): BungeeCord/Velocity setup steps
- [Admin Commands](../admin-commands/commands.md)
