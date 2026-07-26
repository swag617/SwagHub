package com.SwagDev.SwagHub.modules.menu;

import com.SwagDev.SwagHub.SwagHub;
import com.SwagDev.SwagHub.action.ActionContext;
import com.SwagDev.SwagHub.api.event.MenuOpenEvent;
import com.SwagDev.SwagHub.item.ItemBuilder;
import com.SwagDev.SwagHub.item.ItemConfig;
import com.SwagDev.SwagHub.module.Module;
import com.SwagDev.SwagHub.placeholder.SwagHubPlaceholders;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;
import org.bukkit.plugin.PluginManager;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * §5.3 Server Selector / Custom Menus: unlimited custom GUI menus defined under the
 * {@code selector-menus/} folder (§4 — one YAML file per menu), each with a filler
 * item, per-slot items with actions, PlaceholderAPI + SwagHub's own internal
 * placeholders in names/lores, a live-refresh interval, an open sound, and a
 * per-menu permission.
 *
 * <p><b>Utility module — defaults ON regardless of {@code server-role}</b> (§6.2
 * lists "custom menus" among the utility modules that stay enabled on both
 * {@code hub} and {@code game} roles), unlike {@code JoinItemsModule}.</p>
 *
 * <p><b>Identification is holder-based, never title-string matching</b> — see
 * {@link MenuHolder}. Every currently-open menu inventory is tracked per-player in
 * {@link #openSessions}, cleaned up on {@link InventoryCloseEvent}, on
 * {@link PlayerQuitEvent}, and — for every still-open session — on
 * {@link #onDisable()}, so no refresh {@link BukkitTask} is ever leaked (§4).</p>
 *
 * <p><b>Build step 8 — programmatic menus (§5.11's {@code SwagHubAPI#registerMenu}):
 * </b> {@link #programmaticMenus} is a SEPARATE map from {@link #menus} (the
 * file-based {@code selector-menus/*.yml} set), never touched by
 * {@link #loadMenus()}/{@link #onReload()}/{@link #onDisable()} — see
 * {@link #registerMenu(MenuConfig)}'s own javadoc for the full survives-reload /
 * file-wins-on-collision design. Every place that used to consult {@link #menus}
 * directly for "does this id exist" now goes through {@link #lookup(String)} or
 * {@link #allMenuIds()} instead, so {@code /ah open}, the {@code [open-menu]} action,
 * click-routing, and tab-completion all see programmatically-registered menus too.</p>
 */
public class MenuModule extends Module implements Listener {

    private static final String PERMISSION_PREFIX = "swaghub.menu.";
    private static final String MENUS_FOLDER = "selector-menus";
    private static final String DEFAULT_MENU_RESOURCE = MENUS_FOLDER + "/main-menu.yml";

    private final SwagHubPlaceholders placeholders;
    private final Map<UUID, OpenMenuSession> openSessions = new LinkedHashMap<>();

    /** Never cleared by {@link #loadMenus()}/{@link #onReload()}/{@link #onDisable()} — see class javadoc. */
    private final Map<String, MenuConfig> programmaticMenus = new ConcurrentHashMap<>();

    private Map<String, MenuConfig> menus = Collections.emptyMap();

    public MenuModule(SwagHub plugin) {
        super(plugin, "menus");
        this.placeholders = new SwagHubPlaceholders(plugin);
    }

    @Override
    public boolean isEnabledByDefault() {
        return true;
    }

    @Override
    protected void onEnable() {
        seedDefaultMenuIfEmpty();
        loadMenus();
        registerPermissions();
        Bukkit.getPluginManager().registerEvents(this, plugin);
        warnIfBedrockFormsRequested();
    }

    @Override
    protected void onDisable() {
        HandlerList.unregisterAll(this);

        for (UUID uuid : new ArrayList<>(openSessions.keySet())) {
            OpenMenuSession session = openSessions.remove(uuid);
            if (session == null) {
                continue;
            }
            session.cancelRefreshTask();
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.getOpenInventory().getTopInventory().getHolder() instanceof MenuHolder) {
                player.closeInventory();
            }
        }

        unregisterPermissions();
        menus = Collections.emptyMap();
    }

    @Override
    protected void onReload() {
        unregisterPermissions();
        loadMenus();
        registerPermissions();
        warnIfBedrockFormsRequested();
    }

    /**
     * Patch 1, Fix 1 (§8.2) — {@code menus.bedrock-forms} MAY ship as a documented,
     * not-yet-implemented toggle per the design doc's own explicit allowance ("if
     * forms support proves heavy, ship v1 with the toggle present but
     * unimplemented-with-warning rather than blocking the build"). Chest-GUI selector
     * menus already work correctly for Bedrock players through Geyser with ZERO extra
     * code (§8.2's default behavior), so this warning is purely informational — it
     * never changes what actually renders. Logged once per {@link #onEnable()}/{@link
     * #onReload()} cycle, matching {@code CompatibilityManager#recordYield}'s
     * once-per-load-cycle precedent, never once per menu open.
     */
    private void warnIfBedrockFormsRequested() {
        if (plugin.getConfig().getBoolean("menus.bedrock-forms", false)) {
            plugin.getLogger().warning("'menus.bedrock-forms' is enabled in config.yml, but native Bedrock "
                    + "SimpleForm rendering is not implemented yet (§8.2) — Bedrock players will keep seeing "
                    + "the standard chest-GUI selector menus instead, which already work correctly through "
                    + "Geyser. This toggle is a placeholder for a future dedicated forms renderer.");
        }
    }

    // ─── Loading ───

    /** On a genuinely fresh install (folder doesn't exist yet), seeds the §10.2 example selector menu. */
    private void seedDefaultMenuIfEmpty() {
        File folder = new File(plugin.getDataFolder(), MENUS_FOLDER);
        if (!folder.exists()) {
            plugin.saveResource(DEFAULT_MENU_RESOURCE, false);
        }
    }

    private void loadMenus() {
        File folder = new File(plugin.getDataFolder(), MENUS_FOLDER);
        if (!folder.exists() && !folder.mkdirs()) {
            plugin.getLogger().warning("Could not create the '" + MENUS_FOLDER + "' folder — no menus will be available.");
            menus = Collections.emptyMap();
            return;
        }

        File[] files = folder.listFiles((dir, name) -> name.toLowerCase(Locale.ROOT).endsWith(".yml"));
        Map<String, MenuConfig> parsed = new LinkedHashMap<>();
        if (files != null) {
            for (File file : files) {
                String fallbackId = file.getName().substring(0, file.getName().length() - ".yml".length());
                YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
                MenuConfigParser.parse(yaml, fallbackId, plugin.getLogger()::warning).ifPresent(menu -> {
                    String key = menu.getId().toLowerCase(Locale.ROOT);
                    if (parsed.containsKey(key)) {
                        plugin.getLogger().warning("Two menu files resolve to the same id '" + key
                                + "' — the one from '" + file.getName() + "' overwrites the earlier one.");
                    }
                    parsed.put(key, menu);
                });
            }
        }
        menus = Collections.unmodifiableMap(parsed);
    }

    /**
     * Registers one {@code swaghub.menu.<id>} permission per loaded menu (file-based
     * AND programmatic — see {@link #allMenuIds()}), default {@link
     * PermissionDefault#TRUE} — mirrors {@code JoinItemsModule}'s
     * {@code swaghub.item.<id>} pattern exactly (see DECISIONS.md Step 4): menus are
     * openable by everyone by default and can be individually locked down via a
     * permissions plugin, since ids are admin-defined and can't be pre-declared in
     * {@code plugin.yml}.
     */
    private void registerPermissions() {
        for (String id : allMenuIds()) {
            registerPermissionFor(id);
        }
    }

    private void registerPermissionFor(String id) {
        PluginManager pm = plugin.getServer().getPluginManager();
        String node = PERMISSION_PREFIX + id;
        if (pm.getPermission(node) == null) {
            pm.addPermission(new Permission(node, "Grants access to the '" + id + "' menu", PermissionDefault.TRUE));
        }
    }

    private void unregisterPermissions() {
        PluginManager pm = plugin.getServer().getPluginManager();
        for (String id : allMenuIds()) {
            pm.removePermission(PERMISSION_PREFIX + id);
        }
    }

    /** File-based (§5.3) union programmatic (§5.11) menu ids, lower-cased. */
    private Set<String> allMenuIds() {
        Set<String> ids = new java.util.LinkedHashSet<>(menus.keySet());
        ids.addAll(programmaticMenus.keySet());
        return ids;
    }

    /** File-based menus always win an id collision — see {@link #registerMenu(MenuConfig)}. */
    private MenuConfig lookup(String id) {
        if (id == null) {
            return null;
        }
        String key = id.trim().toLowerCase(Locale.ROOT);
        MenuConfig fromFile = menus.get(key);
        return fromFile != null ? fromFile : programmaticMenus.get(key);
    }

    /**
     * §5.11 developer API — the implementation behind {@link
     * com.SwagDev.SwagHub.api.SwagHubAPI#registerMenu(MenuConfig)}. Registers a menu
     * PROGRAMMATICALLY (not backed by a file under {@code selector-menus/}).
     *
     * <p><b>File-based always wins on an id collision</b> (resolved ambiguity,
     * DECISIONS.md Step 8): if {@code menu.getId()} already names a currently-loaded
     * FILE-based menu, this call is rejected (one warning logged, returns
     * {@code false}) rather than silently shadowing an admin-authored menu with a
     * plugin-authored one.</p>
     *
     * <p>Registers {@code swaghub.menu.<id>} immediately, since permissions are
     * otherwise only (re)computed by {@link #registerPermissions()} at enable/reload
     * time — without this, the menu would exist but be unopenable until the next
     * {@code /ah reload}.</p>
     *
     * <p><b>Survives {@link #onReload()} and {@link #onDisable()}</b> (resolved
     * ambiguity, DECISIONS.md Step 8): {@link #programmaticMenus} is never cleared by
     * either — nothing tells a registering third-party plugin to re-register on a
     * SwagHub {@code /ah reload}, and the safest choice is for programmatic
     * registrations to simply persist across both, only ever removed by an explicit
     * {@link #unregisterMenu(String)} call (e.g. from the registering plugin's own
     * {@code onDisable()}).</p>
     */
    public boolean registerMenu(MenuConfig menu) {
        if (menu == null || menu.getId() == null || menu.getId().isBlank()) {
            plugin.getLogger().warning("SwagHubAPI#registerMenu rejected a null/blank-id menu.");
            return false;
        }
        String key = menu.getId().trim().toLowerCase(Locale.ROOT);
        if (menus.containsKey(key)) {
            plugin.getLogger().warning("SwagHubAPI#registerMenu: a file-based menu already owns id '" + key
                    + "' — the file-based menu wins; the programmatic registration was rejected.");
            return false;
        }
        programmaticMenus.put(key, menu);
        registerPermissionFor(key);
        return true;
    }

    /**
     * §5.11 developer API — the implementation behind {@link
     * com.SwagDev.SwagHub.api.SwagHubAPI#unregisterMenu(String)}. Only ever removes
     * from {@link #programmaticMenus} — never touches a file-based menu of the same
     * id (a no-op in that case, not an error), and removes the {@code
     * swaghub.menu.<id>} permission node only if no file-based menu of the same id
     * still owns it.
     */
    public boolean unregisterMenu(String id) {
        if (id == null) {
            return false;
        }
        String key = id.trim().toLowerCase(Locale.ROOT);
        if (programmaticMenus.remove(key) == null) {
            return false;
        }
        if (!menus.containsKey(key)) {
            plugin.getServer().getPluginManager().removePermission(PERMISSION_PREFIX + key);
        }
        return true;
    }

    // ─── Opening ───

    /**
     * Opens {@code menuId} for {@code player}. Returns {@code false} (after sending
     * {@code player} the appropriate feedback message) if the menu doesn't exist or
     * {@code player} lacks {@code swaghub.menu.<id>} — callers ({@code /ah open},
     * {@code OpenMenuAction}) are expected to have already checked
     * {@link #isEnabled()} themselves, mirroring {@code ServerAction}'s pattern for
     * {@code ProxyService}.
     */
    public boolean openMenu(Player player, String menuId) {
        if (player == null || menuId == null) {
            return false;
        }
        MenuConfig menu = lookup(menuId);
        if (menu == null) {
            plugin.getMessageUtil().send(player, "menu-not-found", Map.of("menu", menuId));
            return false;
        }
        if (!player.hasPermission(PERMISSION_PREFIX + menu.getId())) {
            plugin.getMessageUtil().send(player, "menu-no-permission");
            return false;
        }

        Inventory inventory = buildInventory(menu, player);
        player.openInventory(inventory);
        playOpenSound(player, menu.getOpenSound());

        // §5.11 developer API — fire-and-forget/informational only, fired after the
        // inventory is already open (see MenuOpenEvent's own javadoc for why it's
        // deliberately not cancellable).
        Bukkit.getPluginManager().callEvent(new MenuOpenEvent(player, menu));

        OpenMenuSession session = new OpenMenuSession(menu, inventory);
        if (menu.getRefreshIntervalTicks() > 0) {
            long interval = menu.getRefreshIntervalTicks();
            BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin,
                    () -> refreshDynamicSlots(player.getUniqueId()), interval, interval);
            session.setRefreshTask(task);
        }
        openSessions.put(player.getUniqueId(), session);
        return true;
    }

    private Inventory buildInventory(MenuConfig menu, Player viewer) {
        MenuHolder holder = new MenuHolder(menu.getId().toLowerCase(Locale.ROOT));
        Inventory inventory = Bukkit.createInventory(holder, menu.getSize(), plugin.getMessageUtil().parse(menu.getTitle()));
        holder.setInventory(inventory);

        if (menu.getFiller() != null) {
            ItemBuilder.build(plugin, menu.getFiller(), viewer, placeholders).ifPresent(filler -> {
                for (int slot = 0; slot < inventory.getSize(); slot++) {
                    inventory.setItem(slot, filler.clone());
                }
            });
        }
        for (Map.Entry<Integer, ItemConfig> entry : menu.getSlots().entrySet()) {
            ItemBuilder.build(plugin, entry.getValue(), viewer, placeholders)
                    .ifPresent(item -> inventory.setItem(entry.getKey(), item));
        }
        return inventory;
    }

    private void playOpenSound(Player player, String rawSound) {
        if (rawSound == null || rawSound.isBlank()) {
            return;
        }
        try {
            Sound sound = Sound.valueOf(rawSound.trim().toUpperCase(Locale.ROOT));
            player.playSound(player.getLocation(), sound, 1f, 1f);
        } catch (IllegalArgumentException ex) {
            plugin.getLogger().warning("Menu 'open-sound: " + rawSound + "' is not a valid Sound name — skipping the sound.");
        }
    }

    /**
     * Re-renders only the slots whose configured name/lore contains a {@code %}
     * token (a cheap-but-sufficient heuristic for "has a live placeholder") — never
     * the whole menu, and never anything for a menu with {@code refresh-interval-ticks: 0}
     * (no task is ever scheduled for those in the first place).
     */
    private void refreshDynamicSlots(UUID uuid) {
        OpenMenuSession session = openSessions.get(uuid);
        Player player = Bukkit.getPlayer(uuid);
        if (session == null || player == null || !player.isOnline()) {
            return;
        }
        Inventory inventory = session.getInventory();
        for (Map.Entry<Integer, ItemConfig> entry : session.getMenu().getSlots().entrySet()) {
            ItemConfig config = entry.getValue();
            if (!hasDynamicText(config)) {
                continue;
            }
            ItemBuilder.build(plugin, config, player, placeholders)
                    .ifPresent(item -> inventory.setItem(entry.getKey(), item));
        }
    }

    private boolean hasDynamicText(ItemConfig config) {
        if (config.getDisplayName() != null && config.getDisplayName().contains("%")) {
            return true;
        }
        for (String line : config.getLore()) {
            if (line.contains("%")) {
                return true;
            }
        }
        return false;
    }

    // ─── Listeners ───

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof MenuHolder holder)) {
            return;
        }
        // Pure selector menus, never storage — cancel every click regardless of
        // which side (menu or the viewer's own inventory) was clicked.
        event.setCancelled(true);

        if (event.getClickedInventory() == null || !event.getClickedInventory().equals(event.getView().getTopInventory())) {
            return; // clicked their own inventory while the menu is open — nothing to route
        }
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        MenuConfig menu = lookup(holder.getMenuId());
        if (menu == null) {
            return;
        }
        ItemConfig clicked = menu.getSlots().get(event.getSlot());
        if (clicked == null || clicked.getActions().isEmpty()) {
            return;
        }
        ActionContext context = new ActionContext(plugin, player);
        plugin.getActionParser().execute(clicked.getActions(), context);
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof MenuHolder) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof MenuHolder)) {
            return;
        }
        OpenMenuSession session = openSessions.remove(event.getPlayer().getUniqueId());
        if (session != null) {
            session.cancelRefreshTask();
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        OpenMenuSession session = openSessions.remove(event.getPlayer().getUniqueId());
        if (session != null) {
            session.cancelRefreshTask();
        }
    }

    /**
     * Exposed for {@code /ah open}'s tab completion and a later web editor step.
     * Returns the UNION of file-based and programmatic (§5.11) menus — file-based
     * wins on an id collision, matching {@link #lookup(String)}/{@link
     * #registerMenu(MenuConfig)}'s own precedence rule.
     */
    public Map<String, MenuConfig> getMenus() {
        if (programmaticMenus.isEmpty()) {
            return menus;
        }
        Map<String, MenuConfig> combined = new LinkedHashMap<>(programmaticMenus);
        combined.putAll(menus); // file-based overwrites any colliding programmatic entry
        return Collections.unmodifiableMap(combined);
    }
}
