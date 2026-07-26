package com.SwagDev.SwagHub.modules.hologram;

import com.SwagDev.SwagHub.SwagHub;
import com.SwagDev.SwagHub.module.Module;
import com.SwagDev.SwagHub.placeholder.SwagHubPlaceholders;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

/**
 * §5.8 Holograms: one {@link TextDisplay} per line (never a single multi-line
 * entity), stacked vertically below each hologram's anchor location, so
 * {@code addline}/{@code setline}/{@code removeline} can target an individual line
 * without touching the others.
 *
 * <p><b>Compatibility:</b> {@code getConfigKey()} is {@code "holograms"} — already
 * reserved in {@code CompatibilityManager.DEFAULT_CONFLICTS} for SwagCore (this module
 * yields when SwagCore is present, exactly like {@code scoreboard}/{@code tablist}/
 * {@code announcements}/{@code vanish}). Utility module — {@link #isEnabledByDefault()}
 * is always {@code true} regardless of {@code server-role}, matching {@code proxy}/
 * {@code menus}/{@code launchpads}.</p>
 *
 * <p><b>Source of truth is {@code holograms.yml}, never world-saved entity state</b>
 * (resolved ambiguity): every text-display entity this module spawns is tagged with
 * its owning hologram's id ({@link #idKey}, STRING) and its line index
 * ({@link #lineKey}, INTEGER) under a SwagHub {@link NamespacedKey}. Entities are left
 * at Bukkit's own default persistence (survive a normal world save/restart, exactly
 * like any other entity) purely as a performance nicety — it means a graceful restart
 * doesn't have to respawn every hologram from scratch — but this is NEVER relied on
 * for correctness: every enable/{@code /ah reload}/newly-loaded-world re-derives the
 * ENTIRE correct state from {@code holograms.yml} via {@link #reconcileFromWorld},
 * patching (never blindly recreating) whatever it finds. This is why "not
 * persistent-through-a-natural-chunk-unload in a way that matters" is true: nothing
 * about a hologram's correctness depends on any particular entity surviving an
 * unload/reload cycle, only on {@code holograms.yml} itself.</p>
 *
 * <p><b>§6.5 hazard 1 — orphan-cleanup is scoped EXCLUSIVELY to SwagHub's own PDC key</b>
 * (non-negotiable per §0 rule 4): {@link #reconcileFromWorld} iterates every
 * {@link TextDisplay} in a world and immediately {@code continue}s past any entity
 * whose {@link PersistentDataContainer} does NOT have {@link #idKey} set — such an
 * entity is NEVER read further, NEVER removed, NEVER touched in any way, regardless of
 * its own tag/name/owner. Only entities that DO carry {@link #idKey} are evaluated
 * against the current {@code holograms.yml} config to decide "still configured" (left
 * alone, teleported in place if its position is stale) vs. "orphaned" (the hologram
 * was deleted, or shrank below this entity's line index — {@link #despawn} removes
 * ONLY that one entity). This means a coexisting SwagCore hologram (untagged with
 * SwagHub's key, tagged with its own instead) is structurally impossible for this
 * module to ever reach the removal branch for.</p>
 *
 * <p><b>Anti-flicker rendering</b> (mirrors {@code ScoreboardModule}'s established
 * per-line-cache discipline): one shared repeating {@link BukkitTask} resolves each
 * line's text via {@link SwagHubPlaceholders#apply(String, org.bukkit.entity.Player)}
 * with a {@code null} player (a hologram is shared world state with no single "acting
 * player" to resolve player-specific PlaceholderAPI placeholders against — SwagHub's
 * own {@code %swaghub_...%} tokens never need one anyway) then
 * {@code MessageUtil#parse(...)}, only calling {@code TextDisplay#text(Component)} when
 * the resolved string actually changed since the last render for that specific entity
 * ({@link #lastRenderedText}, keyed by entity UUID). Per-hologram
 * {@code refresh-interval-ticks} overrides are honored via the exact same "shared task
 * at the smallest configured interval + per-item elapsed-ticks accumulator" technique
 * {@code AnnouncementsModule} already established, rather than one task per hologram.</p>
 */
public class HologramModule extends Module implements Listener {

    private final NamespacedKey idKey;
    private final NamespacedKey lineKey;
    private final SwagHubPlaceholders placeholders;

    private File hologramsFile;
    private Map<String, HologramConfig> holograms = Collections.emptyMap();
    private final Map<String, TextDisplay[]> liveEntities = new HashMap<>();
    private final Map<UUID, String> lastRenderedText = new HashMap<>();
    private final Map<String, Long> elapsedTicksById = new HashMap<>();

    private long defaultRefreshIntervalTicks = 100L;
    private double lineSpacing = 0.28;
    private long checkIntervalTicks = 100L;
    private BukkitTask renderTask;

    public HologramModule(SwagHub plugin) {
        super(plugin, "holograms");
        this.idKey = new NamespacedKey(plugin, "hologram_id");
        this.lineKey = new NamespacedKey(plugin, "hologram_line");
        this.placeholders = new SwagHubPlaceholders(plugin);
    }

    @Override
    public boolean isEnabledByDefault() {
        return true;
    }

    @Override
    protected void onEnable() {
        hologramsFile = new File(plugin.getDataFolder(), "holograms.yml");
        loadConfig();
        Bukkit.getPluginManager().registerEvents(this, plugin);
        for (World world : Bukkit.getWorlds()) {
            reconcileFromWorld(world);
        }
        scheduleRenderTask();
        renderAll();
    }

    @Override
    protected void onDisable() {
        if (renderTask != null) {
            renderTask.cancel();
            renderTask = null;
        }
        HandlerList.unregisterAll(this);
        for (TextDisplay[] arr : liveEntities.values()) {
            for (TextDisplay td : arr) {
                if (td != null) {
                    despawn(td);
                }
            }
        }
        liveEntities.clear();
        lastRenderedText.clear();
        elapsedTicksById.clear();
        holograms = Collections.emptyMap();
    }

    @Override
    protected void onReload() {
        loadConfig();
        if (renderTask != null) {
            renderTask.cancel();
        }
        scheduleRenderTask();

        Set<String> allIds = new HashSet<>(liveEntities.keySet());
        allIds.addAll(holograms.keySet());
        for (String id : allIds) {
            resyncHologram(id);
        }
    }

    // ─── Loading ───

    private void loadConfig() {
        if (!hologramsFile.exists()) {
            // First-ever load only — plugin.saveResource never re-copies over an existing
            // file, so a later admin edit (or a deliberate delete-and-let-it-regenerate) is
            // respected exactly like scoreboard.yml/launchpads.yml's own established pattern.
            plugin.saveResource("holograms.yml", false);
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(hologramsFile);
        defaultRefreshIntervalTicks = Math.max(1L, yaml.getLong("refresh-interval-ticks", 100L));
        lineSpacing = yaml.getDouble("line-spacing", 0.28);

        holograms = new LinkedHashMap<>(HologramConfigIO.loadAll(hologramsFile, plugin.getLogger()::warning));
        recomputeCheckInterval();
    }

    private void recomputeCheckInterval() {
        long min = defaultRefreshIntervalTicks;
        for (HologramConfig config : holograms.values()) {
            if (config.getRefreshIntervalTicks() != null) {
                min = Math.min(min, config.getRefreshIntervalTicks());
            }
        }
        checkIntervalTicks = Math.max(1L, min);
    }

    private void persist() {
        try {
            HologramConfigIO.saveAll(hologramsFile, holograms);
        } catch (IOException ex) {
            plugin.getLogger().log(Level.SEVERE,
                    "Failed to write holograms.yml — changes may not survive a restart!", ex);
        }
    }

    // ─── World-scan reconciliation (startup + WorldLoadEvent) ───

    /**
     * Scans every {@link TextDisplay} currently loaded in {@code world}, classifying
     * each as "not ours" (untagged/foreign — never touched, §6.5 hazard 1), "orphaned"
     * (ours, but no longer matches a configured hologram+line — removed), or "still
     * valid" (registered into {@link #liveEntities}). Then ensures every configured
     * hologram whose world is {@code world} has an entity for each of its lines,
     * spawning any that are missing and teleporting (never despawning) any whose
     * position has drifted from config.
     */
    private void reconcileFromWorld(World world) {
        for (TextDisplay entity : world.getEntitiesByClass(TextDisplay.class)) {
            PersistentDataContainer pdc = entity.getPersistentDataContainer();
            if (!pdc.has(idKey, PersistentDataType.STRING)) {
                continue; // not ours — never read further, never touched (§6.5 hazard 1)
            }
            String id = pdc.get(idKey, PersistentDataType.STRING);
            Integer lineIndex = pdc.get(lineKey, PersistentDataType.INTEGER);
            HologramConfig config = id != null ? holograms.get(id) : null;

            boolean stillConfigured = config != null
                    && lineIndex != null && lineIndex >= 0 && lineIndex < config.getLines().size()
                    && world.getName().equals(config.getWorldName());

            if (!stillConfigured) {
                despawn(entity);
                continue;
            }

            TextDisplay[] arr = liveEntities.computeIfAbsent(id, k -> new TextDisplay[config.getLines().size()]);
            if (arr.length != config.getLines().size()) {
                arr = Arrays.copyOf(arr, config.getLines().size());
                liveEntities.put(id, arr);
            }
            if (arr[lineIndex] != null) {
                // A duplicate entity somehow already claims this (id, line) slot — remove
                // the extra one defensively rather than losing track of the original.
                despawn(entity);
                continue;
            }
            arr[lineIndex] = entity;
        }

        for (HologramConfig config : holograms.values()) {
            if (world.getName().equals(config.getWorldName())) {
                reconcileHologramInWorld(config, world);
            }
        }
    }

    private void reconcileHologramInWorld(HologramConfig config, World world) {
        int size = config.getLines().size();
        TextDisplay[] arr = liveEntities.computeIfAbsent(config.getId(), k -> new TextDisplay[size]);
        if (arr.length != size) {
            if (arr.length > size) {
                for (int i = size; i < arr.length; i++) {
                    if (arr[i] != null) {
                        despawn(arr[i]);
                    }
                }
            }
            arr = Arrays.copyOf(arr, size);
            liveEntities.put(config.getId(), arr);
        }

        for (int i = 0; i < size; i++) {
            Location expected = lineLocation(config, world, i);
            if (arr[i] == null) {
                arr[i] = spawnLine(config, world, i, expected);
            } else if (!locationsEqual(arr[i].getLocation(), expected)) {
                arr[i].teleport(expected);
            }
        }
    }

    /**
     * Re-derives one hologram's live entities against its current config — used by
     * every command mutation (create/delete/addline/setline/removeline/movehere) and
     * by {@link #onReload()}, since the plugin has been continuously running and
     * {@link #liveEntities} already accurately reflects reality (no world rescan
     * needed, unlike {@link #reconcileFromWorld}, which exists specifically for the
     * "entities may have survived an ungraceful restart we weren't running for" case).
     */
    private void resyncHologram(String id) {
        HologramConfig config = holograms.get(id);
        if (config == null) {
            TextDisplay[] arr = liveEntities.remove(id);
            if (arr != null) {
                for (TextDisplay td : arr) {
                    if (td != null) {
                        despawn(td);
                    }
                }
            }
            elapsedTicksById.remove(id);
            return;
        }

        World world = Bukkit.getWorld(config.getWorldName());
        if (world == null) {
            plugin.getLogger().warning("Hologram '" + id + "' is configured for world '" + config.getWorldName()
                    + "', which is not currently loaded — it will be spawned once that world loads.");
            return;
        }

        TextDisplay[] arr = liveEntities.get(id);
        if (arr != null) {
            boolean worldChanged = false;
            for (TextDisplay td : arr) {
                if (td != null && !td.getWorld().getName().equals(world.getName())) {
                    worldChanged = true;
                    break;
                }
            }
            if (worldChanged) {
                for (TextDisplay td : arr) {
                    if (td != null) {
                        despawn(td);
                    }
                }
                liveEntities.remove(id);
            }
        }

        reconcileHologramInWorld(config, world);
        renderHologram(config);
    }

    private TextDisplay spawnLine(HologramConfig config, World world, int lineIndex, Location location) {
        return world.spawn(location, TextDisplay.class, entity -> {
            entity.setBillboard(Display.Billboard.CENTER); // always faces the viewer, both axes
            entity.setGravity(false);
            entity.setInvulnerable(true);
            // Explicit (matches Bukkit's own default) purely for self-documentation — see
            // class javadoc for why this module never relies on entity persistence for
            // correctness either way.
            entity.setPersistent(true);
            entity.getPersistentDataContainer().set(idKey, PersistentDataType.STRING, config.getId());
            entity.getPersistentDataContainer().set(lineKey, PersistentDataType.INTEGER, lineIndex);
        });
    }

    private void despawn(TextDisplay entity) {
        lastRenderedText.remove(entity.getUniqueId());
        entity.remove();
    }

    private Location lineLocation(HologramConfig config, World world, int lineIndex) {
        return new Location(world, config.getX(), config.getY() - (lineIndex * lineSpacing), config.getZ());
    }

    private boolean locationsEqual(Location a, Location b) {
        return a.getWorld() != null && a.getWorld().equals(b.getWorld())
                && Math.abs(a.getX() - b.getX()) < 0.001
                && Math.abs(a.getY() - b.getY()) < 0.001
                && Math.abs(a.getZ() - b.getZ()) < 0.001;
    }

    @EventHandler
    public void onWorldLoad(WorldLoadEvent event) {
        reconcileFromWorld(event.getWorld());
    }

    // ─── Rendering ───

    private void scheduleRenderTask() {
        long interval = Math.max(1L, checkIntervalTicks);
        renderTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, interval, interval);
    }

    private void tick() {
        for (HologramConfig config : holograms.values()) {
            long effectiveInterval = config.getRefreshIntervalTicks() != null
                    ? config.getRefreshIntervalTicks() : defaultRefreshIntervalTicks;
            effectiveInterval = Math.max(checkIntervalTicks, effectiveInterval);

            long elapsed = elapsedTicksById.merge(config.getId(), checkIntervalTicks, Long::sum);
            if (elapsed < effectiveInterval) {
                continue;
            }
            elapsedTicksById.put(config.getId(), 0L);
            renderHologram(config);
        }
    }

    private void renderAll() {
        for (HologramConfig config : holograms.values()) {
            renderHologram(config);
        }
    }

    private void renderHologram(HologramConfig config) {
        TextDisplay[] arr = liveEntities.get(config.getId());
        if (arr == null) {
            return;
        }
        List<String> lines = config.getLines();
        for (int i = 0; i < arr.length && i < lines.size(); i++) {
            TextDisplay entity = arr[i];
            if (entity == null) {
                continue;
            }
            String resolved = placeholders.apply(lines.get(i), null);
            String cached = lastRenderedText.get(entity.getUniqueId());
            if (!resolved.equals(cached)) {
                entity.text(plugin.getMessageUtil().parse(resolved));
                lastRenderedText.put(entity.getUniqueId(), resolved);
            }
        }
    }

    // ─── Command-facing mutation API ───

    public Map<String, HologramConfig> getHolograms() {
        return Collections.unmodifiableMap(holograms);
    }

    public boolean exists(String id) {
        return holograms.containsKey(id);
    }

    /** @return {@code false} if {@code id} is already taken. */
    public boolean create(String id, Location location, String firstLine) {
        if (holograms.containsKey(id)) {
            return false;
        }
        World world = location.getWorld();
        HologramConfig config = new HologramConfig(id, world != null ? world.getName() : "world",
                location.getX(), location.getY(), location.getZ(), List.of(firstLine), null);
        holograms.put(id, config);
        persist();
        resyncHologram(id);
        return true;
    }

    /** @return {@code false} if {@code id} does not exist. */
    public boolean delete(String id) {
        if (!holograms.containsKey(id)) {
            return false;
        }
        holograms.remove(id);
        persist();
        resyncHologram(id);
        return true;
    }

    /** @return {@code false} if {@code id} does not exist. */
    public boolean addLine(String id, String text) {
        HologramConfig existing = holograms.get(id);
        if (existing == null) {
            return false;
        }
        java.util.List<String> newLines = new java.util.ArrayList<>(existing.getLines());
        newLines.add(text);
        holograms.put(id, withLines(existing, newLines));
        persist();
        resyncHologram(id);
        return true;
    }

    public MutationResult setLine(String id, int index, String text) {
        HologramConfig existing = holograms.get(id);
        if (existing == null) {
            return MutationResult.NOT_FOUND;
        }
        if (index < 0 || index >= existing.getLines().size()) {
            return MutationResult.INVALID_INDEX;
        }
        java.util.List<String> newLines = new java.util.ArrayList<>(existing.getLines());
        newLines.set(index, text);
        holograms.put(id, withLines(existing, newLines));
        persist();
        resyncHologram(id);
        return MutationResult.SUCCESS;
    }

    public MutationResult removeLine(String id, int index) {
        HologramConfig existing = holograms.get(id);
        if (existing == null) {
            return MutationResult.NOT_FOUND;
        }
        if (index < 0 || index >= existing.getLines().size()) {
            return MutationResult.INVALID_INDEX;
        }
        if (existing.getLines().size() <= 1) {
            return MutationResult.LAST_LINE;
        }
        java.util.List<String> newLines = new java.util.ArrayList<>(existing.getLines());
        newLines.remove(index);
        holograms.put(id, withLines(existing, newLines));
        persist();
        resyncHologram(id);
        return MutationResult.SUCCESS;
    }

    /** @return {@code false} if {@code id} does not exist. */
    public boolean moveHere(String id, Location location) {
        HologramConfig existing = holograms.get(id);
        if (existing == null) {
            return false;
        }
        World world = location.getWorld();
        HologramConfig moved = new HologramConfig(id, world != null ? world.getName() : existing.getWorldName(),
                location.getX(), location.getY(), location.getZ(), existing.getLines(), existing.getRefreshIntervalTicks());
        holograms.put(id, moved);
        persist();
        resyncHologram(id);
        return true;
    }

    private HologramConfig withLines(HologramConfig existing, List<String> newLines) {
        return new HologramConfig(existing.getId(), existing.getWorldName(), existing.getX(), existing.getY(),
                existing.getZ(), newLines, existing.getRefreshIntervalTicks());
    }

    public enum MutationResult {
        SUCCESS, NOT_FOUND, INVALID_INDEX, LAST_LINE
    }
}
