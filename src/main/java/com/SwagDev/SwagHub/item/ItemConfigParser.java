package com.SwagDev.SwagHub.item;

import org.bukkit.configuration.ConfigurationSection;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Parses one {@link ConfigurationSection} (an entry under {@code items.yml}'s
 * {@code items:} map, a menu's {@code filler:} block, or one of a menu's
 * {@code slots:} entries) into an {@link ItemConfig}.
 *
 * <p>Deliberately takes only a {@link ConfigurationSection} (Bukkit's config-parsing
 * data model — no live {@code Plugin}/{@code Server} needed, same class the already-
 * proven {@code SpawnLocationIO} builds on top of via {@code YamlConfiguration}) and a
 * plain {@link Consumer} for warnings, rather than a {@code SwagHub}/{@code Logger}
 * reference — this is what keeps it unit-testable with an in-memory
 * {@code YamlConfiguration} in {@code ItemConfigParserTest}, with zero server
 * bootstrap (mirrors the {@code SpawnLocation}/{@code SpawnLocationIO} split, see
 * DECISIONS.md Step 4).</p>
 *
 * <p>Per §10.4, a malformed entry never throws and never aborts anything outside
 * itself — it reports exactly one warning via {@code warnings} and returns
 * {@link Optional#empty()}, letting the caller skip just this one item and keep
 * loading the rest of the file.</p>
 */
public final class ItemConfigParser {

    private ItemConfigParser() {
    }

    /**
     * @param section the item's own config block (e.g. {@code items.<id>},
     *                {@code filler}, or {@code slots.<n>}); {@code null} is treated
     *                as a malformed/missing entry.
     * @param id      an identifying label used only in warning messages (the item id,
     *                or a synthetic label like {@code "<menu-id>:filler"}).
     */
    public static Optional<ItemConfig> parse(ConfigurationSection section, String id, Consumer<String> warnings) {
        if (section == null) {
            warnings.accept("Item '" + id + "' has no configuration body — skipping it.");
            return Optional.empty();
        }

        String material = section.getString("material");
        if (material == null || material.isBlank()) {
            warnings.accept("Item '" + id + "' is missing a 'material' key — skipping it.");
            return Optional.empty();
        }

        int amount = section.getInt("amount", 1);
        if (amount < 1) {
            amount = 1;
        } else if (amount > 64) {
            amount = 64;
        }

        String displayName = section.getString("name", null);
        List<String> lore = section.getStringList("lore");

        Integer customModelData = null;
        if (section.isSet("custom-model-data")) {
            Object raw = section.get("custom-model-data");
            if (raw instanceof Number number) {
                customModelData = number.intValue();
            } else {
                warnings.accept("Item '" + id + "' has a non-numeric 'custom-model-data' value ('" + raw
                        + "') — ignoring it (the item will use its default model).");
            }
        }

        boolean glow = section.getBoolean("glow", false);

        String skullTexture = null;
        String skullOwnerName = null;
        String skullOwnerUuid = null;
        ConfigurationSection skullSection = section.getConfigurationSection("skull");
        if (skullSection != null) {
            skullTexture = skullSection.getString("texture", null);
            skullOwnerName = skullSection.getString("owner-name", null);
            skullOwnerUuid = skullSection.getString("owner-uuid", null);
        }

        List<String> actions = section.getStringList("actions");

        return Optional.of(new ItemConfig(id, material.trim(), amount, displayName, lore, customModelData,
                glow, skullTexture, skullOwnerName, skullOwnerUuid, actions));
    }
}
