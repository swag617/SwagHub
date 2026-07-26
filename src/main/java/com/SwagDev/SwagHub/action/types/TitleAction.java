package com.SwagDev.SwagHub.action.types;

import com.SwagDev.SwagHub.SwagHub;
import com.SwagDev.SwagHub.action.ActionContext;
import com.SwagDev.SwagHub.action.ActionType;
import com.SwagDev.SwagHub.action.ParsedAction;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.entity.Player;

import java.time.Duration;

/**
 * {@code [title] title;subtitle;in;stay;out} — shows a title to the acting player
 * (§5.6/§5.9). {@code in}/{@code stay}/{@code out} are tick counts (20 ticks = 1s).
 * {@code %key%} placeholders are substituted once, over the whole raw argument, before
 * splitting on {@code ;}.
 *
 * <p>Implemented via {@code Player#showTitle(Title)} + {@code Title.title(Component,
 * Component, Title.Times)} + {@code Title.Times.times(Duration, Duration, Duration)} —
 * live-verified against this project's actually-resolved {@code adventure-api:4.17.0}
 * jar (not assumed), see DECISIONS.md Step 5.</p>
 *
 * <p><b>Missing subtitle</b> (two consecutive {@code ;;}, or the argument stops after
 * just the title) resolves to an empty subtitle {@link Component}, never an error.</p>
 *
 * <p><b>Missing/incomplete/non-numeric {@code in}/{@code stay}/{@code out}</b> falls
 * back to {@link Title#DEFAULT_TIMES} (Minecraft's own defaults, 10/70/20 ticks).
 * DECISIONS.md Step 5 records the exact ambiguity resolution: a genuinely INCOMPLETE
 * timing suffix (the author started specifying {@code in}/{@code stay}/{@code out} but
 * didn't finish all three, or one of the three fails to parse as a number) logs one
 * warning naming the malformed action string; simply OMITTING the timing suffix
 * entirely (just {@code title} or {@code title;subtitle}, the common terse form) is
 * treated as a valid, silent "use the defaults" request — warning on every terse,
 * valid usage would contradict this codebase's "never spam the console for valid
 * input" error-handling philosophy (§10.4).</p>
 */
public class TitleAction implements ActionType {

    private final SwagHub plugin;

    public TitleAction(SwagHub plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getTag() {
        return "title";
    }

    @Override
    public void execute(ParsedAction action, ActionContext context) {
        Player player = context.getPlayer();
        if (player == null) {
            return;
        }
        String raw = context.applyPlaceholders(action.getArgument());
        String[] parts = raw.split(";", -1);

        String titleText = parts.length > 0 ? parts[0] : "";
        String subtitleText = parts.length > 1 ? parts[1] : "";

        Title.Times times = Title.DEFAULT_TIMES;
        if (parts.length >= 5) {
            try {
                long in = Long.parseLong(parts[2].trim());
                long stay = Long.parseLong(parts[3].trim());
                long out = Long.parseLong(parts[4].trim());
                times = Title.Times.times(ticksToDuration(in), ticksToDuration(stay), ticksToDuration(out));
            } catch (NumberFormatException ex) {
                plugin.getLogger().warning("A '[title] " + raw + "' action has non-numeric in/stay/out ticks"
                        + " — using Minecraft's default title timing (10/70/20 ticks) instead.");
            }
        } else if (parts.length == 3 || parts.length == 4) {
            // Started specifying a timing suffix but didn't finish all three fields.
            plugin.getLogger().warning("A '[title] " + raw + "' action has an incomplete in/stay/out ticks section"
                    + " (expected 'title;subtitle;in;stay;out') — using Minecraft's default title timing"
                    + " (10/70/20 ticks) instead.");
        }
        // parts.length <= 2 (just title, or title;subtitle): the timing suffix was
        // intentionally omitted — silently use the defaults, no warning.

        Component titleComponent = plugin.getMessageUtil().parse(titleText);
        Component subtitleComponent = plugin.getMessageUtil().parse(subtitleText);
        player.showTitle(Title.title(titleComponent, subtitleComponent, times));
    }

    private static Duration ticksToDuration(long ticks) {
        return Duration.ofMillis(Math.max(0L, ticks) * 50L);
    }
}
