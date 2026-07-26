package com.SwagDev.SwagHub.action.types;

import com.SwagDev.SwagHub.SwagHub;
import com.SwagDev.SwagHub.action.ActionContext;
import com.SwagDev.SwagHub.action.ActionType;
import com.SwagDev.SwagHub.action.ParsedAction;
import com.SwagDev.SwagHub.util.ChatCenterUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;

/**
 * {@code [centered-message] <MiniMessage text>} — sends a chat message horizontally
 * centered in the default chat box (§5.6/§5.9), via {@link ChatCenterUtil}'s pure
 * pixel-width math.
 *
 * <p>Order of operations matters here: {@code %key%} placeholders are substituted and
 * the text is parsed as MiniMessage FIRST (producing the real, formatted
 * {@link Component}); the width math then runs against a PLAIN-text serialization of
 * that already-parsed component (via Adventure's {@link PlainTextComponentSerializer})
 * so gradients/colors never distort the measurement; finally the computed left-padding
 * spaces are prepended to the ORIGINAL formatted component (never the plain-text copy)
 * before sending — this is what keeps colors/gradients intact in the actually-sent
 * message (see DECISIONS.md Step 5).</p>
 */
public class CenteredMessageAction implements ActionType {

    private final SwagHub plugin;

    public CenteredMessageAction(SwagHub plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getTag() {
        return "centered-message";
    }

    @Override
    public void execute(ParsedAction action, ActionContext context) {
        Player player = context.getPlayer();
        if (player == null) {
            return;
        }
        String raw = context.applyPlaceholders(action.getArgument());
        Component formatted = plugin.getMessageUtil().parse(raw);
        String plainText = PlainTextComponentSerializer.plainText().serialize(formatted);

        int padSpaces = ChatCenterUtil.computeLeftPadSpaces(plainText);
        Component toSend = padSpaces > 0 ? Component.text(" ".repeat(padSpaces)).append(formatted) : formatted;
        player.sendMessage(toSend);
    }
}
