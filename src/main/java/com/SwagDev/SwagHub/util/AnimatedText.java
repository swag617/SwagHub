package com.SwagDev.SwagHub.util;

import org.bukkit.configuration.ConfigurationSection;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Pure-data "frame list + interval" shape shared by {@code ScoreboardWorldConfig}'s
 * title, and {@code TablistWorldConfig}'s header/footer (§5.4/§5.5) — a single-element
 * {@code frames} list with {@code frameIntervalTicks: 0} is just static text, so this
 * one class satisfies both the "static" and "animated (frame list + interval)" cases
 * from the design doc with no separate code path. Zero Bukkit dependency beyond
 * {@link ConfigurationSection} (the same config-parsing model type {@code ItemConfigParser}
 * already builds on), so {@link #parse} is unit-testable with an in-memory
 * {@code YamlConfiguration} — exercised indirectly by {@code ScoreboardConfigParserTest}/
 * {@code TablistConfigParserTest} (see DECISIONS.md Step 5).
 */
public final class AnimatedText {

    private final List<String> frames;
    private final long frameIntervalTicks;

    public AnimatedText(List<String> frames, long frameIntervalTicks) {
        this.frames = (frames == null || frames.isEmpty()) ? List.of("") : List.copyOf(frames);
        this.frameIntervalTicks = Math.max(0L, frameIntervalTicks);
    }

    public List<String> getFrames() {
        return frames;
    }

    public long getFrameIntervalTicks() {
        return frameIntervalTicks;
    }

    /**
     * Resolves which frame is showing at {@code tickCounter} (the owning module's own
     * running elapsed-ticks counter — see {@code ScoreboardModule}/{@code TablistModule}).
     * A single frame, or a non-positive interval, always returns frame 0 (static text).
     */
    public String currentFrame(long tickCounter) {
        if (frames.size() == 1 || frameIntervalTicks <= 0) {
            return frames.get(0);
        }
        int index = (int) ((tickCounter / frameIntervalTicks) % frames.size());
        return frames.get(index);
    }

    /**
     * @param section a {@code title}/{@code header}/{@code footer} config block
     *                ({@code frames: [...]}, {@code frame-interval-ticks: N}); {@code null}
     *                is treated as "not configured" (renders as a single empty frame).
     * @param label   identifies the owning field in warning messages, e.g.
     *                {@code "Scoreboard world 'default' title"}.
     */
    public static AnimatedText parse(ConfigurationSection section, String label, Consumer<String> warnings) {
        if (section == null) {
            warnings.accept(label + " is not configured — it will render as empty text.");
            return new AnimatedText(List.of(""), 0L);
        }
        List<String> frames = section.getStringList("frames");
        if (frames.isEmpty()) {
            warnings.accept(label + " has no 'frames' configured — it will render as empty text.");
            frames = List.of("");
        }
        long interval = section.getLong("frame-interval-ticks", 0L);
        if (interval < 0) {
            warnings.accept(label + " has a negative 'frame-interval-ticks' — clamping to 0 (static, no animation).");
            interval = 0L;
        }
        return new AnimatedText(frames, interval);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof AnimatedText other)) {
            return false;
        }
        return frameIntervalTicks == other.frameIntervalTicks && frames.equals(other.frames);
    }

    @Override
    public int hashCode() {
        return Objects.hash(frames, frameIntervalTicks);
    }

    @Override
    public String toString() {
        return "AnimatedText{frames=" + frames.size() + ", frameIntervalTicks=" + frameIntervalTicks + "}";
    }
}
