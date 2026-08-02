package com.SwagDev.SwagHub.modules.joinitems;

import com.SwagDev.SwagHub.SwagHub;
import com.SwagDev.SwagHub.action.ActionContext;
import com.SwagDev.SwagHub.compat.ServerRole;
import com.SwagDev.SwagHub.item.ItemBuilder;
import com.SwagDev.SwagHub.module.Module;
import com.SwagDev.SwagHub.util.HubWorldUtil;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.PluginManager;

import java.io.File;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * §5.2 Custom Join Items: fully configurable hotbar items (material, slot,
 * MiniMessage name/lore, custom model data, head textures, enchant glow), given on
 * join/respawn/entering-a-hub-world, non-movable/non-droppable, and running their
 * configured actions (§5.9) on click. Backed by its own {@code items.yml} (§4's file
 * layout), never appended to {@code config.yml}.
 *
 * <p><b>Module-boundary decision:</b> defaults enabled only when
 * {@code server-role: hub} — §6.2 explicitly lists "join items" in the hub-behavior
 * set that defaults OFF on {@code server-role: game}.</p>
 *
 * <p><b>Join-listener ordering (see DECISIONS.md Step 4):</b> runs at
 * {@link EventPriority#HIGHEST} on {@link PlayerJoinEvent} — deliberately AFTER
 * {@code JoinSettingsModule}'s {@code HIGH}-priority clear-inventory handler, so a
 * join item is never wiped by clear-inventory running afterward. This mirrors the
 * exact SpawnModule -&gt; JoinSettingsModule ordering precedent from build step 2.</p>
 *
 * <p><b>Identification is PDC-only</b> (§5.2): every item this module hands out
 * carries its config id under {@link #ITEM_ID_KEY} in the {@code ItemStack}'s
 * {@code PersistentDataContainer} — material/name matching is never used, since
 * other plugins (or an admin's own inventory items) could coincidentally share
 * either.</p>
 */
public class JoinItemsModule extends Module implements Listener {

    private static final String PERMISSION_PREFIX = "swaghub.item.";

    private final NamespacedKey itemIdKey;

    private Map<String, JoinItemConfig> items = Collections.emptyMap();

    public JoinItemsModule(SwagHub plugin) {
        super(plugin, "join-items");
        this.itemIdKey = new NamespacedKey(plugin, "join_item_id");
    }

    @Override
    public boolean isEnabledByDefault() {
        return plugin.getCompatibilityManager().getServerRole() == ServerRole.HUB;
    }

    @Override
    protected void onEnable() {
        loadItems();
        registerPermissions();
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @Override
    protected void onDisable() {
        HandlerList.unregisterAll(this);
        unregisterPermissions();
        items = Collections.emptyMap();
    }

    @Override
    protected void onReload() {
        unregisterPermissions();
        loadItems();
        registerPermissions();
    }

    // ─── Loading ───

    private void loadItems() {
        File file = new File(plugin.getDataFolder(), "items.yml");
        if (!file.exists()) {
            plugin.saveResource("items.yml", false);
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);

        Map<String, JoinItemConfig> parsed = new LinkedHashMap<>();
        ConfigurationSection itemsSection = yaml.getConfigurationSection("items");
        if (itemsSection != null) {
            for (String id : itemsSection.getKeys(false)) {
                ConfigurationSection itemSection = itemsSection.getConfigurationSection(id);
                JoinItemConfigParser.parse(itemSection, id, plugin.getLogger()::warning)
                        .ifPresent(config -> parsed.put(id, config));
            }
        }
        items = Collections.unmodifiableMap(parsed);
    }

    /**
     * Registers one {@code swaghub.item.<id>} permission per configured item, default
     * {@link PermissionDefault#TRUE} — join items are visible to everyone by default
     * and can be individually revoked via a permissions plugin (§9). Bukkit's
     * permission registry is a flat namespace with no {@code plugin.yml}
     * pre-declaration possible for admin-defined, dynamic ids, so these are
     * registered programmatically here instead (see DECISIONS.md Step 4).
     */
    private void registerPermissions() {
        PluginManager pm = plugin.getServer().getPluginManager();
        for (String id : items.keySet()) {
            String node = PERMISSION_PREFIX + id;
            if (pm.getPermission(node) == null) {
                pm.addPermission(new Permission(node, "Grants access to the '" + id + "' join item", PermissionDefault.TRUE));
            }
        }
    }

    private void unregisterPermissions() {
        PluginManager pm = plugin.getServer().getPluginManager();
        for (String id : items.keySet()) {
            pm.removePermission(PERMISSION_PREFIX + id);
        }
    }

    // ─── Giving items ───

    private void giveItems(Player player) {
        if (!HubWorldUtil.isHubWorld(plugin, player.getWorld())) {
            return;
        }
        for (JoinItemConfig config : items.values()) {
            if (!player.hasPermission(PERMISSION_PREFIX + config.getId())) {
                continue; // skip just this one item, never abort the rest (§10.4-style behavior)
            }
            if (hasCorrectItem(player, config)) {
                continue; // already holding the right item in the right slot — don't duplicate
            }
            ItemBuilder.build(plugin, config.getItem()).ifPresent(built -> {
                ItemStack tagged = tag(built, config.getId());
                player.getInventory().setItem(config.getSlot(), tagged);
            });
        }
    }

    private ItemStack tag(ItemStack item, String id) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.getPersistentDataContainer().set(itemIdKey, PersistentDataType.STRING, id);
            item.setItemMeta(meta);
        }
        return item;
    }

    private boolean hasCorrectItem(Player player, JoinItemConfig config) {
        ItemStack current = player.getInventory().getItem(config.getSlot());
        return config.getId().equals(getTaggedId(current));
    }

    private boolean isJoinItem(ItemStack item) {
        return getTaggedId(item) != null;
    }

    private String getTaggedId(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return null;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return null;
        }
        return meta.getPersistentDataContainer().get(itemIdKey, PersistentDataType.STRING);
    }

    // ─── Listeners ───

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onJoin(PlayerJoinEvent event) {
        giveItems(event.getPlayer());
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        // Respawn hasn't actually applied yet at this point in the event — schedule
        // the give for the next tick so it lands in the post-respawn inventory.
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) {
                giveItems(player);
            }
        });
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        // Only re-give when ENTERING a hub world, never when leaving one (§5.2).
        if (HubWorldUtil.isHubWorld(plugin, event.getPlayer().getWorld())) {
            giveItems(event.getPlayer());
        }
    }

    /**
     * LOWEST priority, not ignoreCancelled: we need to run BEFORE everything else, not
     * after. Cancelling an event doesn't retroactively undo what an earlier-priority
     * handler already did — it only stops handlers that check the flag from running.
     * A prior attempt used HIGHEST priority hoping to "override" other plugins, but by
     * the time a HIGHEST handler runs, e.g. WorldEdit's Navigation Wand (bound to a
     * plain compass by default) had already fired its own NORMAL-priority handler and
     * performed its teleport — cancelling afterward changed nothing. Running at LOWEST
     * and cancelling immediately means WorldEdit's own handler (which, like most
     * well-behaved plugins, checks the cancelled state) never gets a chance to act at
     * all. This only ever fires for items WE tagged (see getTaggedId below), so running
     * first is safe — nothing else has a legitimate claim over a hub-given cosmetic item.
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return; // avoid firing twice (main + off hand) for the same physical click
        }
        if (event.getAction() == Action.PHYSICAL) {
            return;
        }
        ItemStack item = event.getItem();
        String id = getTaggedId(item);
        if (id == null) {
            return;
        }
        event.setCancelled(true);
        JoinItemConfig config = items.get(id);
        if (config == null || config.getItem().getActions().isEmpty()) {
            return;
        }
        ActionContext context = new ActionContext(plugin, event.getPlayer());
        plugin.getActionParser().execute(config.getItem().getActions(), context);
    }

    /**
     * Cancels any click that would move a join item — either directly (the clicked
     * slot or the held cursor carries our PDC tag) or via a number-key hotbar swap
     * that would displace one (the target hotbar slot currently holds a join item).
     */
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (isJoinItem(event.getCurrentItem()) || isJoinItem(event.getCursor())) {
            event.setCancelled(true);
            return;
        }
        if (event.getClick() == ClickType.NUMBER_KEY && event.getWhoClicked() instanceof Player player) {
            ItemStack hotbarItem = player.getInventory().getItem(event.getHotbarButton());
            if (isJoinItem(hotbarItem)) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (isJoinItem(event.getOldCursor())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onDropItem(PlayerDropItemEvent event) {
        if (isJoinItem(event.getItemDrop().getItemStack())) {
            event.setCancelled(true);
        }
    }

    /** Exposed for diagnostics / a later web editor step — never mutated by callers. */
    public Map<String, JoinItemConfig> getItems() {
        return items;
    }
}
