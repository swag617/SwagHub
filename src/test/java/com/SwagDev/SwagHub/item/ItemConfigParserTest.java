package com.SwagDev.SwagHub.item;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link ItemConfigParser} — the pure-data half of §5.2/§5.3's shared
 * item-config model (see {@code ItemBuilder} for the Bukkit-facing, NOT-unit-tested
 * half, and DECISIONS.md Step 4 for why the split exists). Uses only an in-memory
 * {@link YamlConfiguration} — no live Bukkit server, mirroring
 * {@code SpawnLocationIOTest}'s already-proven pattern of exercising Bukkit's
 * config-parsing classes with zero server bootstrap.
 */
class ItemConfigParserTest {

    @Test
    void nullSectionIsSkippedWithAWarning() {
        List<String> warnings = new ArrayList<>();
        Optional<ItemConfig> result = ItemConfigParser.parse(null, "my-item", warnings::add);

        assertTrue(result.isEmpty());
        assertEquals(1, warnings.size());
        assertTrue(warnings.get(0).contains("my-item"));
    }

    @Test
    void missingMaterialIsSkippedWithAWarningNamingTheItemId() {
        YamlConfiguration section = new YamlConfiguration();
        section.set("name", "<red>No material here</red>");
        List<String> warnings = new ArrayList<>();

        Optional<ItemConfig> result = ItemConfigParser.parse(section, "broken-item", warnings::add);

        assertTrue(result.isEmpty());
        assertEquals(1, warnings.size());
        assertTrue(warnings.get(0).contains("broken-item"));
        assertTrue(warnings.get(0).contains("material"));
    }

    @Test
    void blankMaterialIsSkipped() {
        YamlConfiguration section = new YamlConfiguration();
        section.set("material", "   ");

        Optional<ItemConfig> result = ItemConfigParser.parse(section, "blank-material", ignored -> { });

        assertTrue(result.isEmpty());
    }

    @Test
    void minimalSectionProducesSensibleDefaults() {
        YamlConfiguration section = new YamlConfiguration();
        section.set("material", "COMPASS");

        Optional<ItemConfig> result = ItemConfigParser.parse(section, "compass", ignored -> { });

        assertTrue(result.isPresent());
        ItemConfig config = result.get();
        assertEquals("compass", config.getId());
        assertEquals("COMPASS", config.getMaterialName());
        assertEquals(1, config.getAmount());
        assertNull(config.getDisplayName());
        assertTrue(config.getLore().isEmpty());
        assertNull(config.getCustomModelData());
        assertFalse(config.isGlow());
        assertNull(config.getSkullTexture());
        assertTrue(config.getActions().isEmpty());
    }

    @Test
    void amountIsClampedToOneToSixtyFour() {
        YamlConfiguration tooLow = new YamlConfiguration();
        tooLow.set("material", "STONE");
        tooLow.set("amount", 0);
        assertEquals(1, ItemConfigParser.parse(tooLow, "low", ignored -> { }).get().getAmount());

        YamlConfiguration tooHigh = new YamlConfiguration();
        tooHigh.set("material", "STONE");
        tooHigh.set("amount", 500);
        assertEquals(64, ItemConfigParser.parse(tooHigh, "high", ignored -> { }).get().getAmount());
    }

    @Test
    void nonNumericCustomModelDataIsIgnoredWithAWarningButItemStillLoads() {
        YamlConfiguration section = new YamlConfiguration();
        section.set("material", "COMPASS");
        section.set("custom-model-data", "not-a-number");
        List<String> warnings = new ArrayList<>();

        Optional<ItemConfig> result = ItemConfigParser.parse(section, "bad-cmd", warnings::add);

        assertTrue(result.isPresent());
        assertNull(result.get().getCustomModelData());
        assertEquals(1, warnings.size());
        assertTrue(warnings.get(0).contains("custom-model-data"));
    }

    @Test
    void numericCustomModelDataIsParsed() {
        YamlConfiguration section = new YamlConfiguration();
        section.set("material", "COMPASS");
        section.set("custom-model-data", 1001);

        Optional<ItemConfig> result = ItemConfigParser.parse(section, "good-cmd", ignored -> { });

        assertEquals(Integer.valueOf(1001), result.get().getCustomModelData());
    }

    @Test
    void glowNameLoreAndActionsAreAllParsed() {
        YamlConfiguration section = new YamlConfiguration();
        section.set("material", "NETHER_STAR");
        section.set("name", "<light_purple>Fancy</light_purple>");
        section.set("lore", List.of("<gray>Line one</gray>", "<gray>Line two</gray>"));
        section.set("glow", true);
        section.set("actions", List.of("[open-menu] main-menu"));

        Optional<ItemConfig> result = ItemConfigParser.parse(section, "fancy", ignored -> { });

        ItemConfig config = result.get();
        assertEquals("<light_purple>Fancy</light_purple>", config.getDisplayName());
        assertEquals(2, config.getLore().size());
        assertTrue(config.isGlow());
        assertEquals(List.of("[open-menu] main-menu"), config.getActions());
    }

    @Test
    void skullFieldsAreParsedFromTheNestedSkullSection() {
        YamlConfiguration section = new YamlConfiguration();
        section.set("material", "PLAYER_HEAD");
        section.set("skull.texture", "some-base64-value");
        section.set("skull.owner-name", "Notch");
        section.set("skull.owner-uuid", "069a79f4-44e9-4726-a5be-fca90e38aaf5");

        Optional<ItemConfig> result = ItemConfigParser.parse(section, "head", ignored -> { });

        ItemConfig config = result.get();
        assertEquals("some-base64-value", config.getSkullTexture());
        assertEquals("Notch", config.getSkullOwnerName());
        assertEquals("069a79f4-44e9-4726-a5be-fca90e38aaf5", config.getSkullOwnerUuid());
    }
}
