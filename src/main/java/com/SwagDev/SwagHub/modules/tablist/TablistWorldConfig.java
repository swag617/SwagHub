package com.SwagDev.SwagHub.modules.tablist;

import com.SwagDev.SwagHub.util.AnimatedText;

import java.util.Objects;

/**
 * One {@code tablist.yml} {@code worlds.<name>} (or {@code worlds.default}) entry
 * (§5.5): an animated/static header and footer. Zero Bukkit dependency — see
 * {@link TablistConfigParser}.
 */
public final class TablistWorldConfig {

    private final AnimatedText header;
    private final AnimatedText footer;

    public TablistWorldConfig(AnimatedText header, AnimatedText footer) {
        this.header = Objects.requireNonNull(header, "header");
        this.footer = Objects.requireNonNull(footer, "footer");
    }

    public AnimatedText getHeader() {
        return header;
    }

    public AnimatedText getFooter() {
        return footer;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof TablistWorldConfig other)) {
            return false;
        }
        return header.equals(other.header) && footer.equals(other.footer);
    }

    @Override
    public int hashCode() {
        return Objects.hash(header, footer);
    }

    @Override
    public String toString() {
        return "TablistWorldConfig{header=" + header + ", footer=" + footer + "}";
    }
}
