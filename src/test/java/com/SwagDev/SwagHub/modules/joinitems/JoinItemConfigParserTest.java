package com.SwagDev.SwagHub.modules.joinitems;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link JoinItemConfigParser} — specifically the join-item-only
 * "slot" validation layered on top of the shared {@code ItemConfigParser} (already
 * covered by {@code ItemConfigParserTest}). Uses an in-memory
 * {@link YamlConfiguration}, no live Bukkit server — see DECISIONS.md Step 4.
 */
class JoinItemConfigParserTest {

    @Test
    void missingSlotIsSkippedWithAWarning() {
        YamlConfiguration section = new YamlConfiguration();
        section.set("material", "COMPASS");
        List<String> warnings = new ArrayList<>();

        Optional<JoinItemConfig> result = JoinItemConfigParser.parse(section, "no-slot", warnings::add);

        assertTrue(result.isEmpty());
        assertEquals(1, warnings.size());
        assertTrue(warnings.get(0).contains("slot"));
    }

    @Test
    void negativeSlotIsRejected() {
        YamlConfiguration section = new YamlConfiguration();
        section.set("material", "COMPASS");
        section.set("slot", -1);

        Optional<JoinItemConfig> result = JoinItemConfigParser.parse(section, "negative-slot", ignored -> { });

        assertTrue(result.isEmpty());
    }

    @Test
    void slotAboveEightIsRejected() {
        YamlConfiguration section = new YamlConfiguration();
        section.set("material", "COMPASS");
        section.set("slot", 9);
        List<String> warnings = new ArrayList<>();

        Optional<JoinItemConfig> result = JoinItemConfigParser.parse(section, "too-high", warnings::add);

        assertTrue(result.isEmpty());
        assertTrue(warnings.get(0).contains("too-high"));
    }

    @Test
    void validSlotZeroThroughEightAreAccepted() {
        for (int slot = 0; slot <= 8; slot++) {
            YamlConfiguration section = new YamlConfiguration();
            section.set("material", "COMPASS");
            section.set("slot", slot);

            Optional<JoinItemConfig> result = JoinItemConfigParser.parse(section, "slot-" + slot, ignored -> { });

            assertTrue(result.isPresent(), "slot " + slot + " should be accepted");
            assertEquals(slot, result.get().getSlot());
        }
    }

    @Test
    void malformedItemBodyIsSkippedBeforeSlotIsEvenChecked() {
        YamlConfiguration section = new YamlConfiguration();
        section.set("slot", 4);
        // no "material" key at all

        Optional<JoinItemConfig> result = JoinItemConfigParser.parse(section, "no-material", ignored -> { });

        assertTrue(result.isEmpty());
    }
}
