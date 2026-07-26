package com.SwagDev.SwagHub.modules.joinitems;

import com.SwagDev.SwagHub.item.ItemConfig;
import com.SwagDev.SwagHub.item.ItemConfigParser;
import org.bukkit.configuration.ConfigurationSection;

import java.util.Optional;
import java.util.function.Consumer;

/**
 * Parses one {@code items.yml} entry (under {@code items.<id>}) into a
 * {@link JoinItemConfig} — delegates the shared appearance/action fields to
 * {@link ItemConfigParser}, then additionally validates the join-item-specific
 * {@code slot} key (must be a hotbar slot, 0-8).
 *
 * <p>Pure data in, pure data out — no Bukkit {@code Material}/{@code Plugin}
 * dependency, unit-tested directly in {@code JoinItemConfigParserTest} with an
 * in-memory {@code YamlConfiguration}.</p>
 */
public final class JoinItemConfigParser {

    private JoinItemConfigParser() {
    }

    public static Optional<JoinItemConfig> parse(ConfigurationSection section, String id, Consumer<String> warnings) {
        Optional<ItemConfig> itemConfig = ItemConfigParser.parse(section, id, warnings);
        if (itemConfig.isEmpty()) {
            return Optional.empty();
        }
        if (section == null || !section.isSet("slot")) {
            warnings.accept("Join item '" + id + "' is missing a 'slot' key — skipping it.");
            return Optional.empty();
        }
        int slot = section.getInt("slot", -1);
        if (slot < 0 || slot > 8) {
            warnings.accept("Join item '" + id + "' has an out-of-range 'slot' value (" + slot
                    + ") — must be a hotbar slot from 0 to 8 — skipping it.");
            return Optional.empty();
        }
        return Optional.of(new JoinItemConfig(itemConfig.get(), slot));
    }
}
