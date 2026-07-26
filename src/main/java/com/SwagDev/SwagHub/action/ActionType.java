package com.SwagDev.SwagHub.action;

/**
 * One registrable action tag (§5.9), e.g. {@code [message]} or {@code [console]}.
 *
 * <p>Implementations are stateless and registered once with {@link ActionRegistry};
 * all per-execution state lives in the {@link ActionContext}/{@link ParsedAction}
 * passed to {@link #execute}. New action types (later build steps: {@code [server]},
 * {@code [teleport]}, {@code [open-menu]}, {@code [firework]}, {@code [particle]},
 * {@code [effect]}, ...) are added purely by registering a new implementation —
 * {@link ActionParser}'s dispatch loop never needs to change.</p>
 *
 * <p>The three structural guards/sequencing tags — {@code [delay]},
 * {@code [permission-check]}, {@code [chance]} — are handled directly by
 * {@link ActionParser} instead of being {@code ActionType}s, since they control
 * whether/when the REST of the sequence runs rather than doing a single action
 * themselves.</p>
 */
public interface ActionType {

    /** The tag this type registers for, without brackets, e.g. {@code "message"}. Case-insensitive. */
    String getTag();

    /** Executes this action. Exceptions are caught and logged by {@link ActionParser} — never let one propagate silently. */
    void execute(ParsedAction action, ActionContext context);
}
