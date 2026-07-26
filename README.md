# SwagHub

A next-generation hub/lobby core plugin for Paper 1.21.x, built on **SwagAPI**, part of
the Swag plugin ecosystem. SwagHub is a clean-room replacement for DeluxeHub with
first-class BungeeCord *and* Velocity support, MiniMessage everywhere, a fully modular
feature set, and deliberate, documented coexistence with **SwagCore**.

License: **MIT** (see [`LICENSE`](LICENSE)). SwagHub hard-depends on the private,
unpublished `SwagAPI` jar, so full public redistribution isn't in play yet — MIT is the
permissive default until that changes.

---

## Requirements

- **Paper 1.21.x** — this is the only supported platform. No Spigot, no 1.20.x
  backport, no Folia (yet). `pom.xml` compiles against `paper-api 1.21.1-R0.1-SNAPSHOT`
  and `plugin.yml` declares `api-version: '1.21'`; both were the de facto minimum since
  the very first build step, and TextDisplay-based holograms plus other modern Paper
  APIs would make a 1.20.x backport costly for little benefit — matching §12 of the
  design doc's own recommendation.
- **Java 21**.
- **[SwagAPI](https://github.com/) 1.0.0 — hard dependency.** SwagHub declares
  `depend: [SwagAPI]` in `plugin.yml` and will refuse to enable without it (Bukkit
  enforces the load order automatically; SwagHub also double-checks at runtime that
  SwagAPI actually finished enabling, in case it's present but failed its own startup).
  SwagHub owns no database driver, no connection pool, and no player-data store of its
  own — all of that is SwagAPI's job.
- **Optional:** PlaceholderAPI, LuckPerms, Floodgate (soft dependencies — SwagHub
  degrades gracefully with all three absent). See "Bedrock / Floodgate support" below
  for what Floodgate's presence actually enables.
- **Optional, for coexistence:** SwagCore, EssentialsX — SwagHub actively detects both
  and yields overlapping features automatically (see "Coexistence with SwagCore" below).

---

## Feature list

Derived from the module list actually registered in `SwagHub.java` (`registerFeatureModules()`)
and `config.yml`'s module sections — every item below genuinely exists and ships enabled
by default (subject to the server-role defaults described later).

**Hub essentials** — `/setlobby` / `/lobby` (configurable teleport delay + move-cancel),
spawn-on-join, spawn-on-void-fall (teleport instead of death), spawn-on-respawn. World
protection toggles (block break/place, hunger, fall damage, all-damage, PvP with
zone exceptions, weather lock, time lock, mob spawning, item drop/pickup, leaf decay,
fire spread, block burn, TNT). Join settings (clear inventory, set gamemode, heal/feed,
join firework, first-join private message + action-list extras).

**Chat controls** — `/lockchat` (server-wide chat lock), `/clearchat`, per-player chat
cooldown, and a command blocker (blacklist/whitelist mode) — bundled into one
`lockchat` module (plus a separate `clearchat` module, since only `clearchat` yields to
EssentialsX).

**Anti-WorldDownloader** — detects the WDL plugin-messaging channel and kicks or warns,
configurable.

**Join items** — fully configurable hotbar items (material, slot, MiniMessage name/lore,
custom model data, base64/player-name/player-UUID skull textures, enchant glow,
per-item `swaghub.item.<id>` permission), non-movable/non-droppable, restored on
respawn and on entering a hub world. Actions run through the unified action system.

**Server Selector / Custom Menus** — unlimited YAML-defined GUI menus (title, rows,
filler item, per-slot items + actions, live player-count placeholders, refresh
interval, open sound, `swaghub.menu.<id>` permission). `/ah open <menu> [player]`.

**Scoreboard** — per-world MiniMessage sidebar, animated title frames, PlaceholderAPI
support, per-player toggle (`/ah scoreboard`, persisted via SwagAPI's
`IPlayerDataService`), configurable update interval, team-based no-flicker rendering.

**Tablist** — custom header/footer, animation frames, per-world, PlaceholderAPI.

**Announcements** — action-based broadcasts on a per-world interval, sequential or
random rotation, any combination of action tags per entry.

**Movement & Fun** — double jump (permission-gated, particle+sound, region-disable
list, flight-conflict stand-down logic), launchpads (pressure-plate-triggered, config
coordinates), teleport bow (permission-gated, any bow shot becomes a teleport arrow),
player hider (`ALL_VISIBLE → HIDE_OTHERS → RANKS_ONLY` cycle via a join item action,
per-player cooldown).

**Holograms** — native `TextDisplay`-entity holograms (one entity per line), MiniMessage
+ PlaceholderAPI with a refresh interval, `/ah hologram create|delete|addline|setline|
removeline|movehere|list`, PDC-tagged so orphan cleanup on startup never touches another
plugin's `TextDisplay`s (including SwagCore's own hologram system).

**Proxy portals** — config-defined cuboids (wand-selected corners) that proxy-connect
any player who walks in, with a global per-player cooldown to prevent reconnect loops.
`/ah portal wand|create|delete|list`.

**Proxy service** — `Connect`/`ConnectOther`/`PlayerCount`/`PlayerList`/`GetServers`
over the `bungeecord:main` channel (see "Proxy setup" below). `/ah proxy servers`
admin diagnostic.

**Commands & Vanish** — `/fly [player]`, `/gamemode` (+ `/gmc /gms /gma /gmsp`),
`/vanish [player]` — every one of these is individually toggleable and yields to
SwagCore/EssentialsX automatically when installed (see "Coexistence" below).

**PlaceholderAPI expansion** — SwagHub registers its own `swaghub` expansion (see the
placeholder table below) whenever PlaceholderAPI is present and enabled.

**bStats metrics** — toggleable via `metrics.enabled` (default `true`). **The bStats
plugin ID is currently a placeholder (`0`)** — as of Patch 1, this means metrics are
simply not sent at all (one `info` line explains why on startup) rather than being
sent unattributed under id `0`. See "Known limitations" below.

**Update checking** — via SwagAPI's `IUpdateService`; no separate Spigot/Modrinth
poller exists or is needed.

**Developer API** — `SwagHubAPI` (obtained via `SwagHub#getAPI()`) for registering
custom action types and menus programmatically, plus three custom events:
`PlayerSendToServerEvent` (cancellable), `PlayerDoubleJumpEvent` (cancellable),
`MenuOpenEvent`.

**Web editor** — see "Web editor" below.

### Bedrock / Floodgate support (§8 of the design doc — Patch 1)

SwagHub is Bedrock-aware whenever the `floodgate` plugin is present and enabled,
through a single `BedrockService` abstraction (`com.SwagDev.SwagHub.bedrock`) —
feature code never touches Floodgate's own API classes directly, and Floodgate is
detected reflectively (no compile-time Maven dependency on it — see DECISIONS.md's
Patch 1 section for why). On a Java-only server with Floodgate absent, this is a
complete no-op: zero behavior change, one debug-level log line at most.

- **Detection:** `BedrockService#isBedrockPlayer(UUID)` — backed by
  `FloodgateApi#isFloodgatePlayer(UUID)` when Floodgate is present, always `false`
  when it isn't.
- **Placeholders:** `%swaghub_platform%` (`java`/`bedrock`, player-scoped),
  `%swaghub_count_bedrock%`/`%swaghub_count_java%` (online counts on THIS server by
  platform) — see the placeholder table below.
- **Menus:** chest-GUI selector menus (§5.3) already work correctly for Bedrock
  players through Geyser and are the default for everyone. `menus.bedrock-forms`
  (config.yml, default `false`) is a documented, not-yet-implemented toggle for a
  future native Bedrock SimpleForm renderer — enabling it logs one warning and changes
  nothing about how menus actually render.
- **Usernames:** every player-name lookup in SwagHub (command arguments, tab-complete,
  `ConnectOther`, menu open-for-player) already passes Bedrock's `.`-prefixed
  usernames through unmodified — verified across the whole codebase, not assumed.
- **Join items / custom model data:** CMD-based item appearances only render for
  Bedrock players with a mapped Bedrock resource pack; every shipped item still looks
  correct (material + name) without one — see `items.yml`'s own header comment.
- **Double jump:** `double-jump.bedrock` (config.yml, default `true`) — set to `false`
  to disable double jump for Bedrock players specifically if double-tap-space input
  ever proves unreliable through Geyser on your network; Java players are never
  affected by this toggle either way.

Live Bedrock-client verification (an actual connected Geyser/Floodgate player) remains
**owner-verified-pending** — no Geyser/Floodgate test rig has been available in this
project's build environment; see `TEST_CHECKLIST.md`'s Patch 1 section.

---

## Setup guide

### Single server (no proxy)

1. Install `SwagAPI.jar` in `plugins/` — SwagHub will not enable without it.
2. Install `SwagHub.jar` in `plugins/`.
3. Start the server once to generate SwagHub's default config files.
4. Leave `proxy.enabled` alone or set `modules.proxy: false` in `config.yml` — proxy
   features have no effect with no proxy in front of the server, but leaving them on is
   harmless (poll cycles are skipped silently whenever no `Connect` channel exists to
   send them through).
5. Stand where you want players to land and run `/setlobby`.

### BungeeCord network

1. Install `SwagAPI.jar` + `SwagHub.jar` on every backend Paper server that should run
   SwagHub (typically just the hub server — see "Coexistence with SwagCore" below for
   the recommended multi-server topology).
2. **No proxy-side plugin is required.** SwagHub's `ProxyService` talks to BungeeCord
   entirely over the plugin-messaging channel `bungeecord:main` (registered under its
   legacy name `"BungeeCord"`) — this is the same channel BungeeCord has always used.
3. In `config.yml`'s `proxy:` section, list the exact backend server names configured
   in BungeeCord's own `config.yml` under `servers:` so live player counts resolve.
4. That's it — `Connect`/`PlayerCount`/`GetServers` all work immediately.

### Velocity network

1. Same two-jar install as BungeeCord, on every backend server.
2. **Still no proxy-side plugin required.** Velocity answers the `bungeecord:main`
   channel natively via its built-in BungeeCord-compatibility layer, so the exact same
   `BungeeChannelProxyService` implementation works against both proxies unmodified.
3. **One thing to verify on Velocity specifically:** `bungee-plugin-message-channel`
   must be `true` in `velocity.toml`. This is Velocity's own **default** — a stock
   install needs no changes — but if you've deliberately turned it off, SwagHub's proxy
   features have nothing to talk to until it's re-enabled.
4. List backend server names under `proxy.servers` in `config.yml`, matching the
   `[servers]` block in `velocity.toml`, exactly as with BungeeCord.

### `libs/SwagAPI-1.0.0.jar` — dev-time only

The `libs/` folder in this repository holds `SwagAPI-1.0.0.jar` (and a reference copy
of `SwagCore-1.0.0.jar` used only for manual coexistence testing) as a **system-scope
Maven dependency** so the project compiles without a private artifact repository. This
path is **never** read by a running server — a real deployment needs `SwagAPI.jar`
(and, for coexistence, `SwagCore.jar`) placed directly in that server's own `plugins/`
folder, installed like any other plugin.

---

## Coexistence with SwagCore

SwagHub is designed to run alongside **SwagCore** (the ecosystem's Essentials/CMI-style
plugin) on the same network, and sometimes the same server, without fighting it for any
feature. Two mechanisms make this automatic:

- **`server-role: hub | game`** (`config.yml`, default `hub`). On `game` role, hub-behavior
  modules (world protections, forced spawn-on-join, join items, clear-inventory, double
  jump, teleport bow, player hider, anti-WDL, chat lock/cooldown, clearchat, scoreboard,
  tablist, announcements, fly/gamemode/vanish commands) default OFF, while utility
  modules unique to SwagHub (holograms, custom menus, proxy service, portals,
  launchpads) stay ON. Every default can still be overridden individually — role only
  changes defaults, it never hard-locks anything.
- **Auto-yield** (`compatibility.auto-yield: true`, default). SwagHub detects SwagCore
  and EssentialsX at startup by plugin name and automatically disables ("yields") the
  modules each one already owns, logging exactly one console line per yielded module.
  The shipped registry:
  - **SwagCore** → yields `scoreboard`, `tablist`, `announcements`, `join-quit-messages`,
    `holograms`, `vanish`.
  - **EssentialsX** → yields `fly`, `gamemode`, `vanish`, `clearchat`.
  - Extend the registry yourself via `compatibility.conflicts.<PluginName>: [modules...]`
    in `config.yml` — entries there are merged with (never replace) the built-in defaults.

Per-module overrides live under `compatibility.overrides.<module>: auto | enabled |
disabled` (default `auto`). The **recommended hub-server setup**, when SwagCore is also
installed on the hub (the standard topology — SwagCore runs on every backend including
the hub), ships pre-written and commented-out in `config.yml`:

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

Uncomment this block on the hub server only. On pure game servers, leave everything at
`auto` with `server-role: game` — no overrides needed.

Run `/ah info` at any time to see the current server role, every yielded module and
which plugin it yielded to, and every forced override — the single place to diagnose a
misconfiguration.

---

## Web editor

When SwagAPI's shared web service is running, SwagHub's hub-options editor is served at
`/swagapi/swaghub/` (no separate port, no separate login — SwagHub never runs its own
HTTP server or authentication). Requires `swaghub.dashboard.view` (reads) or
`swaghub.dashboard.edit` (writes) — both default `op`.

Current coverage (five JSON endpoints under `/swagapi/swaghub/api/`): a read-only status
page (version, server role, every module's enabled/override/yielded state, proxy status,
pending-update info), and read/write editors for **core options** (server-role,
hub-worlds, per-module enable/disable, compatibility overrides) plus **scoreboard**,
**tablist**, and **announcements**. Modules currently yielded to SwagCore are reported
as read-only with the yield reason, so the UI greys them out ("Managed by SwagCore")
rather than letting you edit a file nothing is actually reading. Holograms, portals,
items, and menus do **not** have a web editor yet — manage those in-game or by hand-editing
their YAML files and running `/ah reload`.

One documented limitation: permission checks for the web editor can only be resolved
against a currently-**online** player (`OfflinePlayer` doesn't expose permission
lookups, and SwagHub deliberately takes no Vault dependency the way SwagCore does). A
staff member granted `swaghub.dashboard.edit` by a permissions plugin is recognized only
while they're online in-game; the same account browsing the panel while offline falls
back to `isOp()`.

---

## Permissions

Transcribed directly from `plugin.yml`'s `permissions:` block.

| Node | Default | Description |
|---|---|---|
| `swaghub.command.reload` | op | Reload SwagHub's configuration and modules |
| `swaghub.command.info` | true | View SwagHub status info |
| `swaghub.command.setlobby` | op | Set the lobby/spawn location |
| `swaghub.command.lobby` | true | Teleport to the lobby/spawn location |
| `swaghub.bypass.lobbydelay` | op | Skip the /lobby teleport delay and move-cancel entirely |
| `swaghub.bypass.build` | op | Bypass the hub's block break/place protection |
| `swaghub.bypass.pvp` | op | Bypass the hub's PvP protection (always allowed to fight) |
| `swaghub.command.proxy` | op | Run the /ah proxy servers admin diagnostic |
| `swaghub.command.open` | true | Open a SwagHub menu for yourself via /ah open \<menu\> |
| `swaghub.command.open.others` | op | Open a SwagHub menu on behalf of another player via /ah open \<menu\> \<player\> |
| `swaghub.command.scoreboard` | true | Toggle your own scoreboard visibility via /ah scoreboard |
| `swaghub.doublejump` | op | Use the double jump ability |
| `swaghub.teleportbow` | op | Shoot bows that teleport you to their landing point |
| `swaghub.playerhider.alwaysvisible` | op | Always stay visible to players in the RANKS_ONLY player-hider state |
| `swaghub.command.fly` | op | Toggle your own flight via /fly |
| `swaghub.command.fly.others` | op | Toggle another player's flight via /fly \<player\> |
| `swaghub.command.gamemode` | op | Change your own game mode via /gamemode |
| `swaghub.command.gamemode.others` | op | Change another player's game mode via /gamemode \<mode\> \<player\> |
| `swaghub.command.vanish` | op | Toggle your own vanish via /vanish |
| `swaghub.command.vanish.others` | op | Toggle another player's vanish via /vanish \<player\> |
| `swaghub.vanish.see` | op | See players who are currently vanished |
| `swaghub.command.lockchat` | op | Toggle the server-wide chat lock via /lockchat |
| `swaghub.bypass.lockchat` | op | Bypass the chat lock and still send messages while it's active |
| `swaghub.bypass.chatcooldown` | op | Bypass the per-message chat cooldown |
| `swaghub.bypass.commandblocker` | op | Bypass the blocked/allowed command list |
| `swaghub.command.clearchat` | op | Clear the chat via /clearchat |
| `swaghub.command.hologram` | op | Manage holograms via /ah hologram \<create\|delete\|addline\|setline\|removeline\|movehere\|list\> |
| `swaghub.command.portal` | op | Manage proxy portals via /ah portal \<wand\|create\|delete\|list\> |
| `swaghub.dashboard.view` | op | View SwagHub's hub-options web editor (read-only) |
| `swaghub.dashboard.edit` | op | Save changes via SwagHub's hub-options web editor |
| `swaghub.admin` | op | Full SwagHub admin access (grant via LuckPerms for a wildcard-style parent) |

Two permission **families** are registered **programmatically at runtime** (one node
per admin-defined id, default `true`) rather than being pre-declared in `plugin.yml`,
since the ids are open-ended:

- `swaghub.item.<id>` — one per entry in `items.yml` (e.g. `swaghub.item.server-selector`).
- `swaghub.menu.<id>` — one per file in `selector-menus/` (e.g. `swaghub.menu.main-menu`).

---

## Placeholders

SwagHub registers a PlaceholderAPI expansion with identifier **`swaghub`** whenever
PlaceholderAPI is present and enabled (`%swaghub_<token>%`). The same resolver
(`SwagHubPlaceholders#resolveIdentifier`) also runs internally — every `%swaghub_...%`
token in a menu/item/scoreboard/tablist/announcement/hologram line resolves even
without PlaceholderAPI installed; PlaceholderAPI is only needed to pull in *other*
plugins' placeholders in that same text.

| Token | Resolves to |
|---|---|
| `%swaghub_count_total%` | Cached total online player count across the whole network |
| `%swaghub_count_<server>%` | Cached player count for one backend server (0 if never polled) |
| `%swaghub_status_<server>%` | `<green>Online</green>` / `<red>Offline</red>` — "online" means at least one `PlayerCount` response has been received for that server name since this server started |
| `%swaghub_vanished%` | `true`/`false` — whether the viewing player is currently vanished |
| `%swaghub_doublejump_enabled%` | `true`/`false` — whether SwagHub's double-jump module currently has flight granted for the viewing player |
| `%swaghub_fly_enabled%` | `true`/`false` — same, for SwagHub's `/fly` grant |
| `%swaghub_playerhider_state%` | The player-hider state enum name, e.g. `ALL_VISIBLE`, `HIDE_OTHERS`, `RANKS_ONLY` |
| `%swaghub_platform%` | `java` or `bedrock` — resolved via `BedrockService` (§8.1, Patch 1) |
| `%swaghub_count_bedrock%` | Online Bedrock player count on THIS server (§8.1, Patch 1) |
| `%swaghub_count_java%` | Online Java player count on THIS server (§8.1, Patch 1) |

Every player-scoped token above resolves to `false` (or `""` for `playerhider_state`,
or `java` for `platform`) rather than throwing when evaluated with no player context
(e.g. console) — a recognized-but-unanswerable placeholder always degrades to
something printable.

---

## Action syntax reference

Every configurable "do something" in SwagHub — join items, menu slots, announcements,
first-join extras — is a list of strings, each starting with a `[tag]`. Parsed by
`ActionParser`, dispatched to a registered `ActionType`. An unrecognized tag or a
malformed line logs one warning naming the problem and is skipped; it never aborts the
rest of the sequence.

**Structural tags** (handled directly by the parser, not `ActionType`s — they control
sequencing/guarding rather than doing one thing):

| Tag | Syntax | Behavior |
|---|---|---|
| `[delay]` | `[delay] <ticks>` | Pauses the rest of the sequence via the Bukkit scheduler (never blocks the calling thread) |
| `[permission-check]` | `[permission-check] <permission>` | If the acting player lacks the permission, every remaining action in the sequence is skipped |
| `[chance]` | `[chance] <percent>` | Rolls a 0–100 chance; on failure, every remaining action in the sequence is skipped |

**Registered action types:**

| Tag | Syntax | Notes |
|---|---|---|
| `[message]` | `[message] <MiniMessage text>` | Chat message to the acting player |
| `[centered-message]` | `[centered-message] <MiniMessage text>` | Chat message horizontally centered via pixel-width math; colors/gradients survive the centering |
| `[actionbar]` | `[actionbar] <MiniMessage text>` | Action bar message |
| `[title]` | `[title] title;subtitle;in;stay;out` | `in`/`stay`/`out` are ticks. Omitting the whole timing suffix (`title` or `title;subtitle`) is valid and silently uses Minecraft's defaults (10/70/20 ticks); starting the suffix but not finishing all three fields logs a warning and also falls back to the defaults |
| `[sound]` | `[sound] SOUND_NAME` or `[sound] SOUND_NAME;volume;pitch` | `volume`/`pitch` default to `1.0`, silently, on a bad/missing value; only an invalid sound *name* logs a warning |
| `[player]` | `[player] <command>` | Runs a command as the acting player (no leading `/`) |
| `[console]` | `[console] <command>` | Runs a command as console |
| `[server]` | `[server] <name>` | Proxy-connects the acting player to a backend server; logs a warning if the proxy module is disabled |
| `[teleport]` | `[teleport] world;x;y;z` or `[teleport] world;x;y;z;yaw;pitch` | `yaw`/`pitch` are both-or-neither — omit both to keep current look direction; a bad world or non-numeric x/y/z aborts the whole action |
| `[open-menu]` | `[open-menu] <menu-id>` | Opens a selector menu for the acting player |
| `[close-menu]` | `[close-menu]` | Closes whatever inventory the acting player currently has open |
| `[firework]` | `[firework] colors;type;flicker;trail` | `colors` — comma-separated `org.bukkit.Color` constant names or `#RRGGBB` hex, default `WHITE`; `type` — `BALL`/`BALL_LARGE`/`STAR`/`BURST`/`CREEPER`, default `BALL`; `flicker`/`trail` — booleans, default `false` |
| `[particle]` | `[particle] ParticleName` or `[particle] ParticleName;count;offsetX;offsetY;offsetZ` | `count` defaults to `10`; each offset defaults to `0.5`; only an invalid particle name logs a warning |
| `[effect]` | `[effect] PotionEffectTypeName` or `[effect] PotionEffectTypeName;durationTicks;amplifier` | `durationTicks` defaults to `200` (10s); `amplifier` defaults to `0` |
| `[cycle-player-hider]` | `[cycle-player-hider]` | Advances the acting player's player-hider state by one step |

`[firework]`/`[particle]`/`[effect]` are SwagHub's own invented argument formats — the
design doc names these tags as required but doesn't specify their exact syntax.

Real usage examples, pulled from the shipped config files:

```yaml
# selector-menus/main-menu.yml — a menu slot
actions:
  - "[server] survival"

# items.yml — a join item
actions:
  - "[open-menu] main-menu"

# announcements.yml
entries:
  - actions:
      - "[actionbar] <yellow>Don't forget to vote for the server!</yellow>"
      - "[sound] ENTITY_EXPERIENCE_ORB_PICKUP;1;1"

# config.yml — join-settings.first-join.actions (commented example)
# actions:
#   - "[delay] 20"
#   - "[message] <gradient:#7b2ff7:#f107a3>Welcome to the network, %player%!</gradient>"
```

---

## Building

```
mvn clean package
```

Produces `target/SwagHub-1.0.0-shaded.jar` (the shade plugin replaces the plain jar with
this relocated/shaded artifact — bStats is the only currently-shaded dependency).
Compiling requires two reference jars under `libs/` (see "`libs/SwagAPI-1.0.0.jar` —
dev-time only" above):

- `libs/SwagAPI-1.0.0.jar` — compiled against as a `system`-scope dependency; hard
  runtime requirement.
- `libs/SwagCore-1.0.0.jar` — kept only as a reference copy for manual coexistence
  testing; never imported or shaded, SwagCore is detected at runtime by plugin name only.

Versioning: `1.0.0`, declared once in `pom.xml`. No changelog or separate versioning
scheme exists beyond that.

---

## Known limitations

See [`HANDOFF_REPORT.md`](HANDOFF_REPORT.md) for the full list of documented,
deliberate trade-offs (offline web-editor permission checks, the placeholder bStats
plugin ID, single global lobby location, etc.) and what has and hasn't been verified on
a live server.
