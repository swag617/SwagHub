package com.SwagDev.SwagHub.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link ChatCenterUtil} — pure {@code String -&gt; int} math, no live
 * server (see DECISIONS.md Step 5). Expected values are hand-verified against the
 * class's own well-known "DefaultFontInfo" pixel-width table (documented in each
 * test's comment).
 */
class ChatCenterUtilTest {

    @Test
    void emptyStringPadsToNearMaxSpaces() {
        // pixelWidth("") = 0 -> halved = 0 -> toCompensate = 154 -> spaceLength = 4
        // -> ceil(154 / 4) = 39 spaces.
        assertEquals(39, ChatCenterUtil.computeLeftPadSpaces(""));
    }

    @Test
    void messageWiderThanTheChatBoxGetsZeroPadding() {
        // 60 'W' chars: pixelWidth = 60 * (5 + 1) = 360 -> halved = 180 >= CENTER_PX (154)
        // -> toCompensate <= 0 -> zero padding.
        String wide = "W".repeat(60);
        assertEquals(0, ChatCenterUtil.computeLeftPadSpaces(wide));
    }

    @Test
    void knownTestStringProducesTheExpectedPadCount() {
        // "Hello World": H5 e5 l1 l1 o5 (space)3 W5 o5 r5 l1 d5, each +1 separator:
        // 6+6+2+2+6+4+6+6+6+2+6 = 52 -> halved = 26 -> toCompensate = 128
        // -> spaceLength = 4 -> 128 / 4 = 32 spaces exactly.
        assertEquals(32, ChatCenterUtil.computeLeftPadSpaces("Hello World"));
    }

    @Test
    void centerPrependsExactlyTheComputedNumberOfSpaces() {
        String centered = ChatCenterUtil.center("Hello World");
        assertTrue(centered.startsWith(" ".repeat(32)));
        assertEquals("Hello World", centered.substring(32));
        assertEquals(32 + "Hello World".length(), centered.length());
    }

    @Test
    void nullInputToCenterReturnsEmptyString() {
        assertEquals("", ChatCenterUtil.center(null));
    }
}
