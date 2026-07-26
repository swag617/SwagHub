package com.SwagDev.SwagHub.modules.scoreboard;

import com.SwagDev.SwagHub.util.AnimatedText;

import java.util.List;
import java.util.Objects;

/**
 * One {@code scoreboard.yml} {@code worlds.<name>} (or {@code worlds.default}) entry
 * (§5.4): an animated/static title and up to 15 sidebar lines, top line first. Zero
 * Bukkit dependency — see {@link ScoreboardConfigParser}.
 */
public final class ScoreboardWorldConfig {

    private final AnimatedText title;
    private final List<String> lines;

    public ScoreboardWorldConfig(AnimatedText title, List<String> lines) {
        this.title = Objects.requireNonNull(title, "title");
        this.lines = lines == null ? List.of() : List.copyOf(lines);
    }

    public AnimatedText getTitle() {
        return title;
    }

    /** Raw MiniMessage/placeholder lines, top-first, never more than {@link ScoreboardConfigParser#MAX_LINES}. */
    public List<String> getLines() {
        return lines;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ScoreboardWorldConfig other)) {
            return false;
        }
        return title.equals(other.title) && lines.equals(other.lines);
    }

    @Override
    public int hashCode() {
        return Objects.hash(title, lines);
    }

    @Override
    public String toString() {
        return "ScoreboardWorldConfig{title=" + title + ", lines=" + lines.size() + "}";
    }
}
