package com.SwagDev.SwagHub.util;

import com.SwagDev.SwagHub.SwagHub;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;
import java.util.logging.Level;

/**
 * MiniMessage parsing + messages.yml loading, used everywhere SwagHub sends
 * user-facing text (§4's "no static abuse" — constructed once by {@link SwagHub}
 * and handed to whatever needs it, not accessed as a static singleton).
 *
 * <p>Adventure/MiniMessage are bundled in Paper — nothing here is shaded.</p>
 */
public class MessageUtil {

    private final SwagHub plugin;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    private FileConfiguration messages;
    private Component prefix;

    public MessageUtil(SwagHub plugin) {
        this.plugin = plugin;
        load();
    }

    /** (Re)loads messages.yml from disk, falling back to the jar's bundled copy for missing keys. */
    public void load() {
        File file = new File(plugin.getDataFolder(), "messages.yml");
        if (!file.exists()) {
            plugin.saveResource("messages.yml", false);
        }
        messages = YamlConfiguration.loadConfiguration(file);

        try (InputStream defaultStream = plugin.getResource("messages.yml")) {
            if (defaultStream != null) {
                YamlConfiguration defaults = YamlConfiguration.loadConfiguration(
                        new InputStreamReader(defaultStream, StandardCharsets.UTF_8));
                messages.setDefaults(defaults);
            }
        } catch (IOException ex) {
            plugin.getLogger().log(Level.WARNING, "Failed to load bundled messages.yml defaults.", ex);
        }

        String configuredPrefix = messages.getString("prefix", "<gray>[<gradient:#7b2ff7:#f107a3>SwagHub</gradient>]</gray> ");
        var prefixService = plugin.getPrefixService();
        String resolvedPrefix = (prefixService != null)
                ? prefixService.getPrefix("SwagHub", configuredPrefix)
                : configuredPrefix;
        prefix = parse(resolvedPrefix);
    }

    /** Parses a raw MiniMessage string with no placeholder substitution. */
    public Component parse(String raw) {
        if (raw == null || raw.isEmpty()) {
            return Component.empty();
        }
        return miniMessage.deserialize(raw);
    }

    /**
     * Parses a raw MiniMessage string, replacing every {@code %key%} token with its
     * value from {@code placeholders} first. (PlaceholderAPI integration, for external
     * placeholders like {@code %player_name%}, arrives with the modules that need it —
     * see §5.11; this method only handles SwagHub's own internal tokens.)
     */
    public Component parse(String raw, Map<String, String> placeholders) {
        if (raw == null || raw.isEmpty()) {
            return Component.empty();
        }
        return parse(applyPlaceholders(raw, placeholders));
    }

    /** Strips all MiniMessage tags, returning plain text. */
    public String strip(String raw) {
        if (raw == null) {
            return "";
        }
        return miniMessage.stripTags(raw);
    }

    public Component getPrefix() {
        return prefix;
    }

    /** Looks up and parses a messages.yml key with no placeholders. */
    public Component getMessage(String path) {
        return getMessage(path, Collections.emptyMap());
    }

    /** Looks up and parses a messages.yml key, substituting {@code %key%} placeholders. */
    public Component getMessage(String path, Map<String, String> placeholders) {
        String raw = messages.getString(path);
        if (raw == null) {
            plugin.getLogger().warning("Missing messages.yml key '" + path + "' — falling back to the key name itself.");
            return Component.text(path);
        }
        return parse(raw, placeholders);
    }

    /** {@link #getMessage(String, Map)} with the configured prefix prepended. */
    public Component getPrefixedMessage(String path, Map<String, String> placeholders) {
        return prefix.append(getMessage(path, placeholders));
    }

    public void send(CommandSender sender, String path) {
        send(sender, path, Collections.emptyMap());
    }

    public void send(CommandSender sender, String path, Map<String, String> placeholders) {
        if (sender == null) {
            return;
        }
        sender.sendMessage(getPrefixedMessage(path, placeholders));
    }

    private String applyPlaceholders(String raw, Map<String, String> placeholders) {
        if (placeholders == null || placeholders.isEmpty()) {
            return raw;
        }
        String result = raw;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) {
                continue;
            }
            result = result.replace("%" + entry.getKey() + "%", entry.getValue());
        }
        return result;
    }
}
