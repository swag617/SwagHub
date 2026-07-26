package com.SwagDev.SwagHub.util;

import java.util.HashMap;
import java.util.Map;

/**
 * Pure chat-centering math for the {@code [centered-message]} action (§5.6/§5.9) — the
 * well-known "DefaultFontInfo" pixel-width technique used across the Spigot plugin
 * ecosystem for years: every character in Minecraft's default font has a known pixel
 * width; a message is centered in the default chat box by left-padding it with just
 * enough space characters to make its rendered width line up at the box's horizontal
 * center (§5.6 spec figure: ~154px, half the default chat box's usable width).
 *
 * <p><b>Pure {@code String -&gt; int}/{@code String} logic, zero Bukkit dependency</b>
 * — the caller ({@code CenteredMessageAction}) is responsible for stripping
 * MiniMessage/Adventure formatting down to plain text (via Adventure's
 * {@code PlainTextComponentSerializer}) BEFORE calling here, and for re-attaching the
 * computed padding to the ORIGINAL formatted {@link net.kyori.adventure.text.Component}
 * afterward — this class only ever sees/returns plain text, so colors/gradients never
 * distort the width math (see DECISIONS.md Step 5).</p>
 */
public final class ChatCenterUtil {

    /**
     * Half of the default chat box's usable pixel width. The classic algorithm compares
     * this against HALF of the message's own pixel width (see
     * {@link #computeLeftPadSpaces(String)}), which is mathematically equivalent to
     * centering the full message within the full ~308px box.
     */
    private static final int CENTER_PX = 154;

    /** Pixel width of the space character alone, from the same font table below. */
    private static final int SPACE_WIDTH = 3;

    /** Fallback width for any character not in the table below. */
    private static final int DEFAULT_WIDTH = 4;

    private static final Map<Character, Integer> WIDTHS = buildWidths();

    private ChatCenterUtil() {
    }

    private static Map<Character, Integer> buildWidths() {
        Map<Character, Integer> w = new HashMap<>();

        // Letters (upper/lower) — width 5 unless noted.
        w.put('A', 5); w.put('a', 5);
        w.put('B', 5); w.put('b', 5);
        w.put('C', 5); w.put('c', 5);
        w.put('D', 5); w.put('d', 5);
        w.put('E', 5); w.put('e', 5);
        w.put('F', 5); w.put('f', 4);
        w.put('G', 5); w.put('g', 5);
        w.put('H', 5); w.put('h', 5);
        w.put('I', 3); w.put('i', 1);
        w.put('J', 5); w.put('j', 5);
        w.put('K', 5); w.put('k', 4);
        w.put('L', 5); w.put('l', 1);
        w.put('M', 5); w.put('m', 5);
        w.put('N', 5); w.put('n', 5);
        w.put('O', 5); w.put('o', 5);
        w.put('P', 5); w.put('p', 5);
        w.put('Q', 5); w.put('q', 5);
        w.put('R', 5); w.put('r', 5);
        w.put('S', 5); w.put('s', 5);
        w.put('T', 5); w.put('t', 4);
        w.put('U', 5); w.put('u', 5);
        w.put('V', 5); w.put('v', 5);
        w.put('W', 5); w.put('w', 5);
        w.put('X', 5); w.put('x', 5);
        w.put('Y', 5); w.put('y', 5);
        w.put('Z', 5); w.put('z', 5);

        // Digits.
        for (char c = '0'; c <= '9'; c++) {
            w.put(c, 5);
        }

        // Symbols.
        w.put('!', 1);
        w.put('@', 6);
        w.put('#', 5);
        w.put('$', 5);
        w.put('%', 5);
        w.put('^', 5);
        w.put('&', 5);
        w.put('*', 5);
        w.put('(', 4);
        w.put(')', 4);
        w.put('-', 5);
        w.put('_', 5);
        w.put('+', 5);
        w.put('=', 5);
        w.put('{', 4);
        w.put('}', 4);
        w.put('[', 3);
        w.put(']', 3);
        w.put(':', 1);
        w.put(';', 1);
        w.put('"', 3);
        w.put('\'', 1);
        w.put('<', 4);
        w.put('>', 4);
        w.put('?', 5);
        w.put('/', 5);
        w.put('\\', 5);
        w.put('|', 1);
        w.put('~', 5);
        w.put('`', 2);
        w.put('.', 1);
        w.put(',', 1);
        w.put(' ', SPACE_WIDTH);

        return w;
    }

    /** Sum of every character's pixel width (plus the standard 1px inter-character gap) in {@code plainText}. */
    public static int pixelWidth(String plainText) {
        if (plainText == null || plainText.isEmpty()) {
            return 0;
        }
        int total = 0;
        for (int i = 0; i < plainText.length(); i++) {
            total += WIDTHS.getOrDefault(plainText.charAt(i), DEFAULT_WIDTH) + 1;
        }
        return total;
    }

    /**
     * How many leading space characters to prepend to {@code plainText} to center it.
     * A message whose pixel width already meets or exceeds {@link #CENTER_PX} times two
     * (i.e. wider than the chat box) gets zero padding, never negative padding.
     */
    public static int computeLeftPadSpaces(String plainText) {
        int halved = pixelWidth(plainText) / 2;
        int toCompensate = CENTER_PX - halved;
        if (toCompensate <= 0) {
            return 0;
        }
        int spaceLength = SPACE_WIDTH + 1;
        int compensated = 0;
        int spaces = 0;
        while (compensated < toCompensate) {
            compensated += spaceLength;
            spaces++;
        }
        return spaces;
    }

    /** {@link #computeLeftPadSpaces(String)}, applied directly to plain text — mainly for tests/diagnostics. */
    public static String center(String plainText) {
        if (plainText == null) {
            return "";
        }
        int spaces = computeLeftPadSpaces(plainText);
        return spaces > 0 ? " ".repeat(spaces) + plainText : plainText;
    }
}
