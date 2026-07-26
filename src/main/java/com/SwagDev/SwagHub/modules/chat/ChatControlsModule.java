package com.SwagDev.SwagHub.modules.chat;

import com.SwagDev.SwagHub.SwagHub;
import com.SwagDev.SwagHub.compat.ServerRole;
import com.SwagDev.SwagHub.module.Module;
import io.papermc.paper.event.player.AsyncChatEvent;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * §5.1 chat controls, deferred from §11 build step 2 into this step — see
 * DECISIONS.md Step 2's "Deferred to a later build step" note. Bundles {@code
 * /lockchat}, chat cooldown, and the command blocker into ONE module with ONE
 * {@link #isEnabledByDefault()}, exactly like {@code WorldProtectionModule} bundles
 * 13 unrelated toggles — none of {@code lockchat}/chat-cooldown/command-blocker have
 * their own entry in {@code CompatibilityManager}'s conflict registry (confirmed
 * absent from both SwagCore's and EssentialsX's mapped module-key sets), so bundling
 * them under one module-key/one default is safe and avoids module-proliferation for
 * three features that are never independently yielded. Module config key is
 * {@code lockchat} (the headline feature) — {@code clearchat} is deliberately a
 * SEPARATE module (see {@code ClearChatModule}), since it alone needs independent
 * EssentialsX-yield behavior (its config key IS compat-reserved) that lockchat/
 * cooldown/blocker must never have.
 *
 * <p><b>Settings location:</b> a {@code lockchat:} section in config.yml, not its own
 * file — same "bounded handful of scalars" reasoning as {@code DoubleJumpModule}'s
 * javadoc explains in full.</p>
 *
 * <p><b>{@code locked} is pure in-memory runtime state, not persisted/read from
 * config (resolved ambiguity):</b> {@code /lockchat} is a live emergency-style admin
 * toggle (matching every other chat-lock plugin's behavior) — it always starts
 * unlocked on module enable/reload, never resuming a locked state across a server
 * restart from a stale config value.</p>
 *
 * <p><b>§6.5 hazard 2 compliance — never re-format/re-render messages:</b> both the
 * lock and cooldown checks run inside {@link #onChat} at {@link EventPriority#LOWEST}
 * and ONLY ever call {@link AsyncChatEvent#setCancelled(boolean)} — never
 * {@code event.message(...)}/{@code event.renderer(...)}, which would step on
 * SwagCore's own chat-rendering ownership when SwagCore is installed.</p>
 */
public class ChatControlsModule extends Module implements Listener, CommandExecutor {

    private static final String PERMISSION_COMMAND = "swaghub.command.lockchat";
    private static final String PERMISSION_BYPASS_LOCKCHAT = "swaghub.bypass.lockchat";
    private static final String PERMISSION_BYPASS_COOLDOWN = "swaghub.bypass.chatcooldown";
    private static final String PERMISSION_BYPASS_COMMANDBLOCKER = "swaghub.bypass.commandblocker";

    // ConcurrentHashMap: read/written from AsyncChatEvent's async thread AND cleared
    // from onQuit on the main thread — must be safe under concurrent access.
    private final Map<UUID, Long> lastMessageMillis = new ConcurrentHashMap<>();

    // volatile: read from AsyncChatEvent's async thread, written from the main thread
    // (the /lockchat command executor and onEnable/onDisable/onReload).
    private volatile boolean locked = false;
    private volatile long cooldownMillis;
    private volatile boolean commandBlockerWhitelist;
    private volatile Set<String> commandBlockerList = new HashSet<>();

    public ChatControlsModule(SwagHub plugin) {
        super(plugin, "lockchat");
    }

    @Override
    public boolean isEnabledByDefault() {
        return plugin.getCompatibilityManager().getServerRole() == ServerRole.HUB;
    }

    @Override
    protected void onEnable() {
        readSettings();
        locked = false;
        Bukkit.getPluginManager().registerEvents(this, plugin);
        PluginCommand command = plugin.getCommand("lockchat");
        if (command != null) {
            command.setExecutor(this);
        } else {
            plugin.getLogger().warning("Could not find the 'lockchat' command — check plugin.yml.");
        }
    }

    @Override
    protected void onDisable() {
        HandlerList.unregisterAll(this);
        lastMessageMillis.clear();
        locked = false;
        PluginCommand command = plugin.getCommand("lockchat");
        if (command != null) {
            command.setExecutor((sender, cmd, label, args) -> {
                plugin.getMessageUtil().send(sender, "module-disabled");
                return true;
            });
        }
    }

    @Override
    protected void onReload() {
        readSettings();
        // locked deliberately NOT reset here — an admin mid-lockdown running /ah reload
        // for an unrelated config change shouldn't silently un-lock chat.
    }

    private void readSettings() {
        var cfg = plugin.getConfig();
        cooldownMillis = Math.max(0L, cfg.getLong("lockchat.cooldown-seconds", 0L)) * 1000L;
        commandBlockerWhitelist = "whitelist".equalsIgnoreCase(cfg.getString("lockchat.command-blocker.mode", "blacklist"));
        List<String> rawCommands = cfg.getStringList("lockchat.command-blocker.commands");
        Set<String> parsed = new HashSet<>();
        for (String raw : rawCommands) {
            if (raw != null && !raw.isBlank()) {
                parsed.add(stripLeadingSlash(raw.trim().toLowerCase(Locale.ROOT)));
            }
        }
        commandBlockerList = parsed;
    }

    private String stripLeadingSlash(String command) {
        return command.startsWith("/") ? command.substring(1) : command;
    }

    // ─── /lockchat ───

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission(PERMISSION_COMMAND)) {
            plugin.getMessageUtil().send(sender, "no-permission");
            return true;
        }
        locked = !locked;
        plugin.getMessageUtil().send(sender, locked ? "lockchat-toggled-on" : "lockchat-toggled-off");
        return true;
    }

    // ─── Chat lock + cooldown ───

    /**
     * Runs on Paper's chat-async thread (per {@link AsyncChatEvent}'s own name/
     * contract), NOT the main thread — {@link #locked}/{@link #cooldownMillis}/
     * {@link #lastMessageMillis} reads here are safe (simple volatile-free reads of
     * primitives/a concurrent-safe-enough map for this use), but sending the actual
     * feedback message is deliberately hopped onto the main thread via
     * {@code Bukkit.getScheduler().runTask(...)} rather than calling
     * {@code player.sendMessage(...)} directly from this thread — per this project's
     * own "never access Bukkit API off main thread" rule (CLAUDE.md), even though
     * Adventure message sends are commonly considered safe cross-thread in practice.
     */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();

        if (locked && !player.hasPermission(PERMISSION_BYPASS_LOCKCHAT)) {
            event.setCancelled(true);
            Bukkit.getScheduler().runTask(plugin, () -> plugin.getMessageUtil().send(player, "chat-locked-blocked"));
            return;
        }

        if (cooldownMillis > 0 && !player.hasPermission(PERMISSION_BYPASS_COOLDOWN)) {
            long now = System.currentTimeMillis();
            long last = lastMessageMillis.getOrDefault(player.getUniqueId(), 0L);
            long remainingMs = (last + cooldownMillis) - now;
            if (remainingMs > 0) {
                event.setCancelled(true);
                String seconds = String.valueOf((remainingMs + 999) / 1000);
                Bukkit.getScheduler().runTask(plugin, () ->
                        plugin.getMessageUtil().send(player, "chat-cooldown-blocked", Map.of("seconds", seconds)));
                return;
            }
            lastMessageMillis.put(player.getUniqueId(), now);
        }
    }

    // ─── Command blocker ───

    @EventHandler(ignoreCancelled = true)
    public void onCommandPreprocess(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (player.hasPermission(PERMISSION_BYPASS_COMMANDBLOCKER)) {
            return;
        }
        String base = extractBaseCommand(event.getMessage());
        boolean inList = commandBlockerList.contains(base);
        boolean shouldBlock = commandBlockerWhitelist != inList;
        if (shouldBlock) {
            event.setCancelled(true);
            plugin.getMessageUtil().send(player, "command-blocked");
        }
    }

    private String extractBaseCommand(String rawMessage) {
        String withoutSlash = rawMessage.startsWith("/") ? rawMessage.substring(1) : rawMessage;
        int spaceIndex = withoutSlash.indexOf(' ');
        String base = spaceIndex >= 0 ? withoutSlash.substring(0, spaceIndex) : withoutSlash;
        int slashIndex = base.indexOf(':'); // strip a "plugin:command" qualifier, if present
        if (slashIndex >= 0) {
            base = base.substring(slashIndex + 1);
        }
        return base.toLowerCase(Locale.ROOT);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        lastMessageMillis.remove(event.getPlayer().getUniqueId());
    }
}
