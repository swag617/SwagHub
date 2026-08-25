# Chat & Utility Commands

Six small modules covering chat moderation and the everyday staff commands (`/fly`, `/gamemode`, `/vanish`). All six default on only when `server-role: hub`, and `fly`/`gamemode`/`vanish`/`clearchat` are **compat-reserved** — they yield automatically to SwagCore's or EssentialsX's own equivalents when either is detected. See [Coexistence with SwagCore](../ecosystem/coexistence.md).

## Chat Lock, Cooldown & Command Blocker (`module: lockchat`)

Bundles three related features into one module, since none of them individually appear in the SwagCore/EssentialsX conflict registry — bundling them avoids module-proliferation for features that are never independently yielded.

```yaml
lockchat:
  cooldown-seconds: 0
  command-blocker:
    mode: blacklist
    commands: []
```

```
/lockchat
```

Requires `swaghub.command.lockchat` (default `op`). Toggles a server-wide chat lock. `locked` is pure in-memory runtime state — **not** persisted or read from config — it always starts unlocked on module enable/reload, never resuming a locked state across a restart. `swaghub.bypass.lockchat` (default `op`) always allows sending messages regardless.

**Chat cooldown** — `cooldown-seconds` is the minimum time between messages per player (`0` disables the check entirely). `swaghub.bypass.chatcooldown` (default `op`) always bypasses it.

**Command blocker** — `command-blocker.mode: blacklist` blocks only the commands listed under `commands` (everything else allowed); `whitelist` allows *only* the listed commands (everything else blocked). Base command names only, no leading `/` or arguments — e.g. listing `gamemode` also blocks plugin-qualified variants like `/gamemode:gamemode`. `swaghub.bypass.commandblocker` (default `op`) always bypasses it.

## Clear Chat (`module: clearchat`)

A **separate** module from `lockchat`, since only `clearchat` needs independent EssentialsX-yield behavior.

```yaml
clearchat:
  lines: 100
  clear-for-everyone: true
```

```
/clearchat
```

Requires `swaghub.command.clearchat` (default `op`). Sends `lines` blank lines. `clear-for-everyone: true` clears chat for every online player; `false` clears it only for whoever ran the command (this toggle has no effect when run from console, since there's no "everyone but the console" distinction to make there).

## Anti-WorldDownloader (`module: anti-wdl`)

```yaml
anti-wdl:
  action: kick
```

Detects the WorldDownloader mod registering its plugin-messaging channel and either `kick`s the player immediately or sends a `warn`-ing chat message instead — never both, never silently ignored.

## `/fly [player]` (`module: fly`)

Requires `swaghub.command.fly` (self, default `op`) or `swaghub.command.fly.others` (default `op`). Toggles `allowFlight` + `flying`. Follows the [flight stand-down contract](movement-and-extras.md#the-flight-stand-down-contract) shared with double jump: only ever grants flight it doesn't already see as `true`, and only ever revokes a grant it made itself — it refuses to act at all on a target currently in creative or spectator mode, since forcibly revoking `allowFlight` there would leave them unable to fly despite still being in a gamemode that grants it. Unlike double jump, `/fly` has no world-exit auto-disable — flight stays on until explicitly toggled off, matching how EssentialsX's own `/fly` behaves.

```
/flyspeed <1-10> [player]
```

Also in this module — see [Movement & Extras](movement-and-extras.md#gamemode--flight-persistence) for the full detail, including how a chosen speed survives a relog.

## `/gamemode <mode> [player]` (`module: gamemode`)

Aliases: `/gmc` `/gms` `/gma` `/gmsp`. Requires `swaghub.command.gamemode` (self) or `swaghub.command.gamemode.others` (both default `op`). Every alias routes to the same handler — invoked via an alias, the target mode is inferred from which alias was typed; invoked as the literal `/gamemode`, an explicit mode argument is required. Accepts full mode names and vanilla's own short forms (`s`/`c`/`a`/`sp`), case-insensitive.

## `/vanish [player]` (`module: vanish`)

Requires `swaghub.command.vanish` (self) or `swaghub.command.vanish.others` (both default `op`). `swaghub.vanish.see` (default `op`) — a **passive visibility** permission, not a command permission — lets a staff member see other vanished players.

Built entirely on `Player#hidePlayer`/`#showPlayer`, which already fully removes the target from every non-permitted viewer's tab list and client-side entity rendering — no separate invisibility potion effect layered on top. Vanish state is persisted, so a vanished staff member's next relog doesn't un-vanish them or announce their join/quit.

## Related Pages

- [Movement & Extras](movement-and-extras.md)
- [Coexistence with SwagCore](../ecosystem/coexistence.md)
- [Permissions](../permissions/permissions.md)
