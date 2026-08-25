# Welcome to SwagHub

> **SwagHub** is a next-generation hub/lobby core plugin for Minecraft servers running Paper 1.21.x. It's a clean-room, feature-parity-plus-improvements replacement for DeluxeHub — spawn/lobby persistence, world protection, join items, a YAML-defined server-selector menu engine, scoreboard/tablist/announcements, movement extras, holograms, proxy portals, and first-class BungeeCord *and* Velocity support with **zero proxy-side plugin required**.

> **Status: feature-complete, live-verification pending.** All planned build steps are implemented and `mvn -o clean package` passes 87/87 unit tests. What hasn't happened yet is running SwagHub on a real Paper 1.21.x server — this project has never had a live test server, BungeeCord/Velocity proxy, or connected Bedrock client available during development. Everything that's been verified was verified by code inspection or by extracting and inspecting the packaged jar; everything else is documented as such rather than implied. See [Known Limitations](troubleshooting/known-limitations.md) before you rely on anything network- or Bedrock-related in production.

## What Makes SwagHub Special?

* **Hub Essentials** — `/setlobby` writes the lobby location to disk **immediately** (never only on shutdown), fixing the exact DeluxeHub bug class this plugin exists to avoid. `/lobby` supports a configurable teleport delay with move-cancel, plus spawn-on-join, spawn-on-void-fall, and spawn-on-respawn. Full world protection (block break/place, hunger, fall/all damage, PvP with zone exceptions, weather/time lock, mob spawning, item drop/pickup, leaf decay, fire spread, TNT). See [Hub Essentials](core-features/hub-essentials.md).
* **Config-Driven Join Items** — hotbar items defined entirely in `items.yml` (material, slot, MiniMessage name/lore, custom model data, skull textures, glow) and identified **only** by an internal PDC tag — never material or name — so they can never collide with a real item. Every item auto-registers its own `swaghub.item.<id>` permission. See [Join Items](core-features/join-items.md).
* **Scoreboard, Tablist & Announcements** — per-world MiniMessage sidebar and header/footer with animated title frames, PlaceholderAPI support, a per-player `/ah scoreboard` toggle, and action-based rotating announcements. See [Scoreboard & Tablist](core-features/scoreboard-tablist.md).
* **Network-Aware Player Counts** — `%swaghub_count_total%` and per-server counts, live over the same `bungeecord:main` plugin-messaging channel BungeeCord has always used — which Velocity also answers natively, so **one implementation drives both proxies with no proxy-side jar at all**. See [Network-Aware Player Counts](core-features/network-player-counts.md).
* **YAML Server-Selector Menus** — unlimited custom GUI menus with live player-count/status placeholders, auto-refreshing slots, filler items, and open sounds. Openable from join items, other menus, or `/ah open <menu> [player]`. See [Server Selector Menus](core-features/server-selector-menus.md).
* **Movement & Fun** — double jump (region-disable list, flight-conflict stand-down logic), pressure-plate launchpads, a teleport bow, and a cyclable player-hider (`ALL_VISIBLE → HIDE_OTHERS → RANKS_ONLY`). See [Movement & Extras](core-features/movement-and-extras.md).
* **Native Holograms & Proxy Portals** — `TextDisplay`-entity holograms managed entirely via `/ah hologram`, and wand-selected cuboid portals that proxy-connect any player who walks in. See [Holograms & Portals](core-features/holograms-and-portals.md).
* **Bedrock / Floodgate-Aware** — a single `BedrockService` abstraction, detected reflectively (no compile-time Floodgate dependency), with a complete no-op fallback on a Java-only server. See [Bedrock / Floodgate Support](core-features/bedrock-floodgate.md).
* **Deliberate SwagCore Coexistence** — a `server-role: hub | game` setting plus automatic conflict auto-yield means SwagHub and SwagCore never fight over scoreboard, tablist, vanish, or announcements on the same network. See [Coexistence with SwagCore](ecosystem/coexistence.md).

## Core Philosophy

### Every Module Is Independently Reversible
Every feature is a `Module` with its own `onEnable`/`onDisable`/`onReload`, wrapped so one module's exception can never take down another. `/ah reload` re-evaluates every module's desired state against the freshly-reloaded config — enabling, disabling, or reloading each one as needed, with no restart required.

### Role, Not a Hard Lock
`server-role: hub | game` only changes each module's *default* — hub-behavior modules (world protection, join items, scoreboard, tablist, fly/gamemode/vanish, and more) default off on `game` role, while utility modules (proxy, menus, holograms, portals, launchpads, the web editor) stay on regardless. Every default can still be overridden individually.

### Config-Driven Content
Join items, menus, holograms, portals, launchpads, and announcements are all defined in YAML, hand-editable and hot-reloadable via `/ah reload`. A malformed entry logs one specific warning naming the id and the problem, and is skipped — it never aborts the rest of the file.

## Quick Links

| Topic | Description | Link |
|-------|-------------|------|
| **Installation** | Get SwagHub running in a few minutes | [Installation Guide](getting-started/installation.md) |
| **Network-Aware Player Counts** | How the live proxy-wide counts actually work | [Network-Aware Player Counts](core-features/network-player-counts.md) |
| **Admin Commands** | Full command reference | [Admin Commands](admin-commands/commands.md) |
| **Known Limitations** | What's deliberately deferred or unverified | [Known Limitations](troubleshooting/known-limitations.md) |

## What's Not Finished (Or Not Yet Verified)

SwagHub is honest about where it stands. A few things worth knowing up front:

* **No live Paper server has ever run this plugin.** Every feature is implemented and unit-tested where that's meaningfully possible (87 plain-data/parser/IO tests, zero live Bukkit dependency), but `onEnable()`, event handling, and scheduler behavior have only been verified by code inspection — not by actually starting a server. See [Known Limitations](troubleshooting/known-limitations.md).
* **Bedrock support is implemented but owner-verified-pending.** No Geyser/Floodgate test rig has been available during development — the detection code, placeholders, and per-platform toggles are all in place, but a real connected Bedrock client has never confirmed them end-to-end.
* **The bStats plugin ID is still a placeholder (`0`).** As shipped, this means metrics are simply never sent (one log line explains why) — a real id must be registered before any public release.
* **The web editor's offline-admin permission check falls back to `isOp()`.** A non-op staff member granted `swaghub.dashboard.edit` by a permissions plugin is only recognized by the web editor while they're online in-game.
* **Only one global lobby location is supported** — no per-world lobbies in this version.

A complete, itemized list — including which of these are code-inspectable as provably correct versus genuinely untested — lives on the [Known Limitations](troubleshooting/known-limitations.md) page.

## Community

Bug reports and feedback are welcome via [GitHub Issues](https://github.com/swag617/SwagHub/issues).

## Credits

**Developer:** SwagDev
**Built With:** Java 21, Paper API, SwagAPI

## License

SwagHub is licensed under the **MIT License** — see [`LICENSE`](https://github.com/swag617/SwagHub/blob/main/LICENSE). It hard-depends on the private, unpublished `SwagAPI` jar, so full public redistribution isn't in play yet; MIT is the permissive default until that changes.

---

> **Need Help?** Check the [Troubleshooting](troubleshooting/troubleshooting.md) page or open a GitHub Issue.
