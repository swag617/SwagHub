# Known Limitations

SwagHub is honest about where its own testing stops. This page separates **deliberate, documented trade-offs** from things that simply haven't been verified live yet; both are worth reading before you rely on a specific behavior in production.

## The Big One: No Live Paper Server Has Ever Run This Plugin

Every build step of this project independently notes the same constraint: no Paper 1.21.x test server, no BungeeCord/Velocity proxy, and no connected Bedrock client has been available at any point in development. `mvn -o clean package` passes **87/87 unit tests**, but every one of those is a plain-data/parser/IO test (config parsing, YAML round-trips) with zero live Bukkit dependency. None of them exercise `onEnable()`, event handling, or scheduler behavior, which is exactly the gap live-server testing would close.

What that means in practice:

- **Provably correct by code inspection:** the `/setlobby` persistence guarantee, the module-disable listener/task cleanup pattern, `/ah reload`'s config re-read, and the compatibility-manager yield-decision logic.
- **Implemented but not yet observed live:** actual proxy connectivity against a real Velocity/BungeeCord install, actual coexistence with a running SwagCore instance, and whether a clean boot genuinely produces zero console errors.

If you run SwagHub in production, treat the manual-test items below as your own acceptance checklist, not as pre-verified guarantees.

## Bedrock Support: Implemented, Owner-Verified-Pending

Every piece of §8 Bedrock support (`BedrockService`, the platform placeholders, `double-jump.bedrock`, `menus.bedrock-forms`) is implemented and code-reviewed, but no Geyser/Floodgate test rig has ever been available during development. A real connected Bedrock client has never confirmed any of it end-to-end. See [Bedrock / Floodgate Support](../core-features/bedrock-floodgate.md).

## The bStats Plugin ID Is Still a Placeholder

`SwagHub.BSTATS_PLUGIN_ID` is currently `0`. With a placeholder id, metrics are **never initialized or sent at all**: one info-level log line explains why on every startup with `metrics.enabled: true`, rather than data being sent unattributed under id `0`. **A real id must be registered at [bstats.org/what-is-my-plugin-id](https://bstats.org/what-is-my-plugin-id) before any public release.**

## Web Editor: Offline Permission Checks Fall Back to `isOp()`

`OfflinePlayer` doesn't expose permission lookups, and SwagHub deliberately takes no Vault dependency the way SwagCore does. A non-op staff member granted `swaghub.dashboard.edit` by a permissions plugin is only recognized by the web editor while they're online in-game; the same account browsing the panel while offline is denied unless they're a real server operator. See [Web Editor](../ecosystem/web-editor.md).

## Only One Global Lobby Location

`SpawnStore` holds exactly one lobby location; there is no per-world lobby support in this version. See [Hub Essentials](../core-features/hub-essentials.md).

## Player-Hider's `alwaysvisible` Permission Isn't Polled Live

A permission change made via a permissions plugin while a player is already online is only re-evaluated the next time that specific pair of players triggers a join/quit/cycle event, not instantly. See [Movement & Extras](../core-features/movement-and-extras.md#player-hider).

## `[bossbar]` Is Not an Implemented Action Type

Despite prose in the plugin's own design notes mentioning "bossbar" alongside chat/title/actionbar/sound as an example announcement action, no `[bossbar]` tag exists in the [action system](../core-features/join-items.md#the-action-system); the enumerated required-tag list never actually included it. A legitimate candidate for a future action type.

## Web Editor Coverage Gaps

Holograms, portals, join items, and server-selector menus do **not** have a web editor yet; manage those in-game or by hand-editing their YAML files and running `/ah reload`. See [Web Editor](../ecosystem/web-editor.md).

## Reference Jars Are Dev-Time Only

`libs/SwagAPI-1.0.0.jar` and `libs/SwagCore-1.0.0.jar` in the plugin's source repository are compile-time/manual-testing conveniences, never read by a running server. A real deployment needs the actual `SwagAPI.jar` (and, for coexistence, `SwagCore.jar`) in that server's own `plugins/` folder.

## Related Pages

- [Troubleshooting](troubleshooting.md)
- [Bedrock / Floodgate Support](../core-features/bedrock-floodgate.md)
- [Web Editor](../ecosystem/web-editor.md)
