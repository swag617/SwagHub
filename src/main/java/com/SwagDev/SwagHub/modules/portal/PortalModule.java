package com.SwagDev.SwagHub.modules.portal;

import com.SwagDev.SwagHub.SwagHub;
import com.SwagDev.SwagHub.module.Module;
import com.SwagDev.SwagHub.modules.proxy.ProxyService;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * §3.3/§5.8 Proxy portals: config-defined cuboids ({@link PortalRegion}) that
 * {@code connect()} any player who walks into them to a configured backend server,
 * with a per-player cooldown to prevent reconnect loops.
 *
 * <p><b>Compatibility:</b> {@code getConfigKey()} is {@code "portals"} — deliberately
 * NOT present in {@code CompatibilityManager.DEFAULT_CONFLICTS} for any plugin (§6.3's
 * do-not-yield list explicitly names portals as staying enabled even with SwagCore
 * present). Utility module — {@link #isEnabledByDefault()} is always {@code true}
 * regardless of {@code server-role}, matching {@code proxy}/{@code menus}/
 * {@code launchpads}/{@code holograms}.</p>
 *
 * <p><b>Not action-list-driven</b> (resolved per §3.3's own wording, unlike menus/
 * items/announcements): entering a portal calls
 * {@link ProxyService#connect(Player, String)} directly — there is no
 * {@code ActionContext}/{@code ActionParser} round trip here, since a portal has
 * exactly one thing it ever does.</p>
 *
 * <p><b>Detection is {@link PlayerMoveEvent} with a cheap block-coordinate early
 * exit</b> (§4's "no per-tick work when idle" performance rule, applied to the
 * genuinely hot {@code PlayerMoveEvent} rather than a rarer event like launchpads'
 * {@code Action.PHYSICAL}): a pure look-direction-only move (same block coordinates,
 * same world) returns immediately before any region check runs.</p>
 *
 * <p><b>Cooldown scope (resolved ambiguity): GLOBAL per-player, not per-(player,
 * portal).</b> §3.3's own framing — "a per-player cooldown... to prevent reconnect
 * loops" — reads most naturally as a single timer per player that blocks ANY portal
 * re-trigger for a short window, since that is what actually stops a bounce-back loop
 * (the destination server sending them right back through a portal near their spawn
 * point there, for instance) regardless of whether it's literally the same portal
 * cuboid or a different one. {@link #lastTriggerMillis} is keyed only by player UUID.
 * The cooldown DURATION used for a given trigger is still the triggered portal's own
 * override (or the module default) — only the "last triggered at" timestamp is shared
 * across all portals for that player.</p>
 *
 * <p><b>Cooldown-blocked re-entry is silent — no message</b> (resolved ambiguity): a
 * player standing inside (or wobbling in and out of) a portal's cuboid during the
 * cooldown window would otherwise be spammed with a "please wait" message on every
 * single blocked {@code PlayerMoveEvent}, which is worse than saying nothing —
 * matching {@code LaunchpadModule}'s own precedent of never messaging on the common,
 * expected "nothing happened" path.</p>
 *
 * <p><b>Wand tool, PDC-only identification</b> (mirrors {@code JoinItemsModule}'s
 * exact precedent, never material/name matching): {@code /ah portal wand} gives a
 * {@code STICK} tagged with {@link #wandKey} (a BYTE marker). Left-click a block sets
 * corner 1, right-click a block sets corner 2, both cancelled so the wand never
 * actually breaks/interacts with the world. The in-progress {@link Selection} is
 * per-player, in-memory only ({@link #selections}) — deliberately NOT persisted; losing
 * a selection on plugin restart/relog is expected and harmless for a selection tool
 * (mirrors WorldEdit's own session-only selection semantics).</p>
 *
 * <p><b>Hub-worlds gating: deliberately NOT applied</b> (resolved ambiguity, shared
 * with {@code HologramModule} — see DECISIONS.md Step 7): portals are explicitly
 * admin-PLACED at specific chosen locations via wand selection, unlike world-WIDE
 * behaviors like {@code world-protection}/{@code scoreboard} that apply across an
 * entire configured world. An admin placing a portal on a {@code server-role: game}
 * server (where SwagHub might be installed only for these unique utility features)
 * should have it work regardless of whether that world happens to be listed in
 * {@code hub-worlds} — mirrors why {@code /setlobby}'s own location isn't gated either.</p>
 */
public class PortalModule extends Module implements Listener {

    private final NamespacedKey wandKey;
    private File portalsFile;

    private Map<String, PortalRegion> portals = Collections.emptyMap();
    private long defaultCooldownSeconds = 3L;

    private final Map<UUID, Long> lastTriggerMillis = new ConcurrentHashMap<>();
    private final Map<UUID, Selection> selections = new ConcurrentHashMap<>();

    public PortalModule(SwagHub plugin) {
        super(plugin, "portals");
        this.wandKey = new NamespacedKey(plugin, "portal_wand");
    }

    @Override
    public boolean isEnabledByDefault() {
        return true;
    }

    @Override
    protected void onEnable() {
        portalsFile = new File(plugin.getDataFolder(), "portals.yml");
        loadConfig();
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @Override
    protected void onDisable() {
        HandlerList.unregisterAll(this);
        lastTriggerMillis.clear();
        selections.clear();
        portals = Collections.emptyMap();
    }

    @Override
    protected void onReload() {
        loadConfig();
    }

    // ─── Loading ───

    private void loadConfig() {
        if (!portalsFile.exists()) {
            plugin.saveResource("portals.yml", false);
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(portalsFile);
        defaultCooldownSeconds = Math.max(0L, yaml.getLong("cooldown-seconds", 3L));
        portals = new LinkedHashMap<>(PortalRegionIO.loadAll(portalsFile, plugin.getLogger()::warning));
    }

    private void persist() {
        try {
            PortalRegionIO.saveAll(portalsFile, portals);
        } catch (IOException ex) {
            plugin.getLogger().log(Level.SEVERE,
                    "Failed to write portals.yml — changes may not survive a restart!", ex);
        }
    }

    // ─── Trigger detection ───

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null) {
            return;
        }
        // Cheap early exit: never process a pure look-direction-only move (§4).
        if (from.getWorld() == to.getWorld()
                && from.getBlockX() == to.getBlockX()
                && from.getBlockY() == to.getBlockY()
                && from.getBlockZ() == to.getBlockZ()) {
            return;
        }

        for (PortalRegion portal : portals.values()) {
            if (portal.contains(to)) {
                tryTrigger(event.getPlayer(), portal);
                return; // first-match wins — a player is only ever meaningfully "in" one portal per move
            }
        }
    }

    private void tryTrigger(Player player, PortalRegion portal) {
        long cooldownMillis = (portal.getCooldownSecondsOverride() != null
                ? portal.getCooldownSecondsOverride() : defaultCooldownSeconds) * 1000L;

        long now = System.currentTimeMillis();
        Long last = lastTriggerMillis.get(player.getUniqueId());
        if (last != null && now - last < cooldownMillis) {
            return; // still on cooldown — silently do nothing (resolved ambiguity, see class javadoc)
        }

        ProxyService service = plugin.getProxyService();
        if (service == null || !service.isEnabled()) {
            plugin.getLogger().warning("A portal ('" + portal.getId() + "') tried to send " + player.getName()
                    + " to a backend server, but the proxy module is disabled (see 'modules.proxy'/'proxy:' in"
                    + " config.yml) — enable it to use portals.");
            return;
        }

        lastTriggerMillis.put(player.getUniqueId(), now);
        service.connect(player, portal.getServerName());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        lastTriggerMillis.remove(event.getPlayer().getUniqueId());
        selections.remove(event.getPlayer().getUniqueId());
    }

    // ─── Wand ───

    public ItemStack createWandItem() {
        ItemStack item = new ItemStack(Material.STICK);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(plugin.getMessageUtil().parse(
                    "<gradient:#7b2ff7:#f107a3><bold>Portal Wand</bold></gradient>"));
            meta.getPersistentDataContainer().set(wandKey, PersistentDataType.BYTE, (byte) 1);
            item.setItemMeta(meta);
        }
        return item;
    }

    private boolean isWand(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.getPersistentDataContainer().has(wandKey, PersistentDataType.BYTE);
    }

    @EventHandler(ignoreCancelled = true)
    public void onWandInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return; // avoid double-firing for the same physical click
        }
        if (!isWand(event.getItem())) {
            return;
        }
        Block clicked = event.getClickedBlock();
        if (clicked == null) {
            return;
        }

        Player player = event.getPlayer();
        Selection selection = selections.computeIfAbsent(player.getUniqueId(), k -> new Selection());

        if (event.getAction() == Action.LEFT_CLICK_BLOCK) {
            event.setCancelled(true); // wand never actually breaks/interacts with the world
            selection.corner1 = clicked.getLocation();
            plugin.getMessageUtil().send(player, "portal-wand-corner1-set");
        } else if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            event.setCancelled(true);
            selection.corner2 = clicked.getLocation();
            plugin.getMessageUtil().send(player, "portal-wand-corner2-set");
        }
    }

    // ─── Command-facing mutation API ───

    public Map<String, PortalRegion> getPortals() {
        return Collections.unmodifiableMap(portals);
    }

    public boolean exists(String id) {
        return portals.containsKey(id);
    }

    public enum CreateResult {
        SUCCESS, ALREADY_EXISTS, NO_SELECTION, DIFFERENT_WORLDS
    }

    public CreateResult create(Player sender, String id, String serverName) {
        if (portals.containsKey(id)) {
            return CreateResult.ALREADY_EXISTS;
        }
        Selection selection = selections.get(sender.getUniqueId());
        if (selection == null || selection.corner1 == null || selection.corner2 == null) {
            return CreateResult.NO_SELECTION;
        }
        World world1 = selection.corner1.getWorld();
        World world2 = selection.corner2.getWorld();
        if (world1 == null || world2 == null || !world1.equals(world2)) {
            return CreateResult.DIFFERENT_WORLDS;
        }

        PortalRegion region = new PortalRegion(id, world1.getName(),
                selection.corner1.getX(), selection.corner1.getY(), selection.corner1.getZ(),
                selection.corner2.getX(), selection.corner2.getY(), selection.corner2.getZ(),
                serverName, null);
        portals.put(id, region);
        persist();
        return CreateResult.SUCCESS;
    }

    /** @return {@code false} if {@code id} does not exist. */
    public boolean delete(String id) {
        if (!portals.containsKey(id)) {
            return false;
        }
        portals.remove(id);
        persist();
        return true;
    }

    /** Per-player, in-memory-only WorldEdit-style two-corner selection state. */
    private static final class Selection {
        private Location corner1;
        private Location corner2;
    }
}
