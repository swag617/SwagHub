# Bedrock / Floodgate Support

SwagHub is Bedrock-aware whenever the **`floodgate`** plugin is present and enabled, through a single `BedrockService` abstraction. Feature code never touches Floodgate's own API classes directly — Floodgate is detected **reflectively**, with no compile-time Maven dependency on it. On a Java-only server with Floodgate absent, this is a complete no-op: zero behavior change, at most one debug-level log line.

> **Not yet live-verified.** No Geyser/Floodgate test rig has been available during this project's development. Everything below is implemented and code-reviewed, but a real connected Bedrock client has never confirmed it end-to-end. See [Known Limitations](../troubleshooting/known-limitations.md).

## How Detection Works

`FloodgateBedrockService` loads `org.geysermc.floodgate.api.FloodgateApi` via reflection at startup. Every reflective call is wrapped so a Floodgate API surface change, a missing method, or any other reflection failure can never propagate into feature code — it degrades this one detector to reporting no Bedrock players (with one logged warning), never a plugin-wide failure. When Floodgate isn't installed at all, a no-op implementation is used instead, and `isBedrockPlayer(...)` always returns `false`.

```
BedrockService#isBedrockPlayer(UUID)
```

Backed by `FloodgateApi#isFloodgatePlayer(UUID)` when Floodgate is present; always `false` when it isn't.

## Placeholders

| Token | Resolves to |
|---|---|
| `%swaghub_platform%` | `java` or `bedrock`, resolved via `BedrockService` — player-scoped. |
| `%swaghub_count_bedrock%` | Online Bedrock player count **on this server**, by platform. |
| `%swaghub_count_java%` | Online Java player count on this server. |

## Menus

Chest-GUI selector menus (see [Server Selector Menus](server-selector-menus.md)) already work correctly for Bedrock players through Geyser and are the default for everyone. `menus.bedrock-forms` in `config.yml` (default `false`) is a documented, **not-yet-implemented** toggle for a future native Bedrock SimpleForm renderer — enabling it logs one console warning on enable/reload and changes nothing about how menus actually render.

## Usernames

Every player-name lookup in SwagHub — command arguments, tab-complete, `ConnectOther`, opening a menu for another player — already passes Bedrock's `.`-prefixed usernames through unmodified. This was verified across the whole codebase, not assumed.

## Join Items & Custom Model Data

Custom-model-data item appearances only render for Bedrock players who have a mapped Bedrock resource pack. Every shipped item still displays correctly (real material + name/lore) without one — design your own items so they look fine without custom model data too. See [Join Items](join-items.md).

## Double Jump

```yaml
double-jump:
  bedrock: true
```

Double jump uses the same client-side double-tap-space input as vanilla flight, which Bedrock clients relay through Geyser — this works for Bedrock players by default. Set `double-jump.bedrock: false` to disable double jump specifically for Bedrock players if that input ever proves unreliable on your network; Java players are never affected by this toggle either way. See [Movement & Extras](movement-and-extras.md#double-jump-module-double-jump).

## Related Pages

- [Configuration](../getting-started/configuration.md)
- [Server Selector Menus](server-selector-menus.md)
- [Known Limitations](../troubleshooting/known-limitations.md)
