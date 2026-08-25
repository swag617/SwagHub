# Troubleshooting

## Plugin Won't Enable

**Check, in this order:**

1. **Is SwagAPI installed?** SwagHub `depend`s on SwagAPI and refuses to enable without it. Bukkit enforces the load order automatically, but SwagHub also double-checks at runtime that SwagAPI actually *finished* enabling, in case it's present but failed its own startup.
2. **Java version:** SwagHub is compiled for Java 21, matching Paper 1.21's own minimum.
3. **Server type:** built against `api-version: '1.21'`; use Paper (or a compatible fork) on 1.21.x. No Spigot, no 1.20.x backport, no Folia.
4. Console log for the actual startup exception.

## `/lobby` Says "Not Set" After a Restart

This is the exact bug class SwagHub was built to avoid. `/setlobby` writes `data/spawn.yml` to disk **immediately**, never only on shutdown, specifically so a hard crash can't lose it. If you're still seeing this:

- Confirm you actually ran `/setlobby` (not just walked to a spot) and got the confirmation message.
- Check `plugins/SwagHub/data/spawn.yml` exists and contains real coordinates.
- Confirm the `spawn` module is actually enabled (`/ah info`); it always defaults on regardless of `server-role`, so this would only happen if `modules.spawn: false` was explicitly set.

## Scoreboard / Tablist / Announcements Not Showing

1. Confirm `server-role: hub` in `config.yml`; all three default **off** on `server-role: game`.
2. Confirm your current world is listed in `hub-worlds` (or that list is empty, meaning every world).
3. Run `/ah info` and check whether the module was **yielded to SwagCore**. If SwagCore is also installed, `scoreboard`/`tablist`/`announcements` yield to it automatically unless you've uncommented the recommended hub-server override block in `config.yml`. See [Coexistence with SwagCore](../ecosystem/coexistence.md).
4. For the scoreboard specifically, confirm the player hasn't toggled it off with `/ah scoreboard`.

## Join Items Aren't Given

1. Confirm `server-role: hub`; join items default **off** on `server-role: game`.
2. Confirm the player has the item's specific `swaghub.item.<id>` permission (default `true`, but can be individually revoked).
3. Confirm the player is actually standing in a `hub-worlds` world; items are only given on join, on respawn, and when entering a hub world, never while already outside one.
4. Check console for a malformed-entry warning naming the item id: a bad `material` or out-of-range `slot` causes that one item to be skipped while everything else still loads.

## Live Player Counts Always Read `0`

1. Confirm the `proxy` module is actually enabled (`modules.proxy`, or just leave it unset; it defaults on).
2. Confirm the server name in `%swaghub_count_<server>%` (or a menu slot's placeholder) **exactly matches** a name in both `proxy.servers` in `config.yml` **and** the proxy's own server list (Velocity's `[servers]` block or BungeeCord's `servers:` section).
3. Confirm at least one player has been online on this server since the last poll. `PlayerCount`/`GetServers` can only be sent through a connected player's channel, so with zero players online the poll cycle is silently skipped and stale cached values (possibly still `0` on a fresh server) keep being served.
4. On Velocity, confirm `bungee-plugin-message-channel: true` in `velocity.toml` (this is Velocity's own default; only relevant if you've deliberately disabled it).

See [Network-Aware Player Counts](../core-features/network-player-counts.md) for the full mechanism.

## `/ah networkstats` Returns No Data

1. Confirm `network.shared-secret` in `config.yml` matches the **target** server's own SwagAPI `network.shared-secret` exactly.
2. Confirm the server id you queried is a key under `network.known-servers`, and that its URL is reachable from this machine.
3. A slow, offline, or unconfigured server always yields "no data" rather than an error; this is the documented fallback behavior, not a bug. See [Network-Aware Player Counts](../core-features/network-player-counts.md#network-stats-cross-server-player-lookups).

## A Feature I Enabled Isn't Running

Run `/ah info` first. It shows every module's registered state, every yielded module and which plugin it yielded to, and every active override. Most "a toggle I set isn't working" reports turn out to be a module that's yielded to SwagCore or EssentialsX rather than actually disabled by your own config. See [Coexistence with SwagCore](../ecosystem/coexistence.md).

## Bedrock Players Aren't Being Detected

1. Confirm the `floodgate` plugin is actually installed **and enabled**. SwagHub only detects it reflectively at startup; installing it fresh requires a restart, not just `/ah reload`.
2. Check the startup log for a `FloodgateApi could not be loaded reflectively` warning: this means Floodgate is present but its API surface didn't match what SwagHub expects (e.g. a very old or very new Floodgate build), and Bedrock players will be treated as Java players until it's resolved.
3. This entire area is **owner-verified-pending**. See [Known Limitations](known-limitations.md).

## Still Stuck?

Open a [GitHub Issue](https://github.com/swag617/SwagHub/issues) with your server log and relevant config files.

## Related Pages

- [Known Limitations](known-limitations.md)
- [Configuration](../getting-started/configuration.md)
- [Coexistence with SwagCore](../ecosystem/coexistence.md)
