package com.SwagDev.SwagHub.modules.menu;

import com.SwagDev.SwagHub.item.ItemConfig;
import com.SwagDev.SwagHub.item.ItemConfigParser;
import org.bukkit.configuration.ConfigurationSection;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Parses one {@code selector-menus/*.yml} file's root section into a
 * {@link MenuConfig}. Pure data in, pure data out — no Bukkit {@code Inventory}/
 * {@code Plugin} dependency, unit-tested directly in {@code MenuConfigParserTest}.
 *
 * <p>A menu's id comes from its own {@code id:} key if present, otherwise falls
 * back to {@code fallbackId} (the filename, minus extension — the caller's job to
 * supply). Documented choice: an explicit {@code id:} lets an admin rename the file
 * on disk without breaking {@code [open-menu]}/{@code /ah open} references that
 * already point at the old id.</p>
 *
 * <p>Per §10.4: a malformed {@code slots.<n>} entry (non-numeric key, out-of-range
 * slot, or an otherwise-malformed item body) logs one specific warning and is
 * skipped — the rest of the menu (and the rest of the folder) still loads.</p>
 */
public final class MenuConfigParser {

    private static final int MIN_ROWS = 1;
    private static final int MAX_ROWS = 6;

    private MenuConfigParser() {
    }

    public static Optional<MenuConfig> parse(ConfigurationSection root, String fallbackId, Consumer<String> warnings) {
        if (root == null) {
            warnings.accept("Menu file '" + fallbackId + "' is empty or unreadable — skipping it.");
            return Optional.empty();
        }

        String id = root.getString("id", fallbackId);
        if (id == null || id.isBlank()) {
            id = fallbackId;
        }
        id = id.trim();

        String title = root.getString("title", "<white>Menu</white>");

        int rows = root.getInt("rows", 3);
        if (rows < MIN_ROWS) {
            warnings.accept("Menu '" + id + "' has 'rows: " + rows + "' — clamping to " + MIN_ROWS + ".");
            rows = MIN_ROWS;
        } else if (rows > MAX_ROWS) {
            warnings.accept("Menu '" + id + "' has 'rows: " + rows + "' — clamping to " + MAX_ROWS + " (max chest size).");
            rows = MAX_ROWS;
        }

        ItemConfig filler = null;
        ConfigurationSection fillerSection = root.getConfigurationSection("filler");
        if (fillerSection != null) {
            filler = ItemConfigParser.parse(fillerSection, id + ":filler", warnings).orElse(null);
        }

        Map<Integer, ItemConfig> slots = new LinkedHashMap<>();
        ConfigurationSection slotsSection = root.getConfigurationSection("slots");
        if (slotsSection != null) {
            int size = rows * 9;
            for (String key : slotsSection.getKeys(false)) {
                int slot;
                try {
                    slot = Integer.parseInt(key.trim());
                } catch (NumberFormatException ex) {
                    warnings.accept("Menu '" + id + "' has a non-numeric slot key '" + key + "' under 'slots:' — skipping it.");
                    continue;
                }
                if (slot < 0 || slot >= size) {
                    warnings.accept("Menu '" + id + "' has an out-of-range slot '" + slot + "' for a " + rows
                            + "-row menu (valid range: 0-" + (size - 1) + ") — skipping it.");
                    continue;
                }
                ConfigurationSection slotSection = slotsSection.getConfigurationSection(key);
                int finalSlot = slot;
                ItemConfigParser.parse(slotSection, id + ":slot" + slot, warnings)
                        .ifPresent(item -> slots.put(finalSlot, item));
            }
        }

        long refreshIntervalTicks = Math.max(0L, root.getLong("refresh-interval-ticks", 0L));
        String openSound = root.getString("open-sound", null);

        return Optional.of(new MenuConfig(id, title, rows, filler, slots, refreshIntervalTicks, openSound));
    }
}
