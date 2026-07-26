package com.SwagDev.SwagHub.action;

/**
 * One parsed line from an action list, e.g. the config string
 * {@code "[message] <green>Hello %player%!"} becomes
 * {@code new ParsedAction("message", "<green>Hello %player%!")}.
 */
public final class ParsedAction {

    private final String tag;
    private final String argument;

    public ParsedAction(String tag, String argument) {
        this.tag = tag;
        this.argument = argument;
    }

    /** Lower-cased tag with no brackets, e.g. {@code "message"}. */
    public String getTag() {
        return tag;
    }

    /** Everything after the {@code [tag]} prefix, trimmed. May be empty but never null. */
    public String getArgument() {
        return argument;
    }

    @Override
    public String toString() {
        return "[" + tag + "] " + argument;
    }
}
