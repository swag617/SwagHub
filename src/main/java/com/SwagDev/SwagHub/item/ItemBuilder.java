package com.SwagDev.SwagHub.item;

import com.SwagDev.SwagHub.SwagHub;
import com.SwagDev.SwagHub.placeholder.SwagHubPlaceholders;
import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Builds a real Bukkit {@link ItemStack} from an {@link ItemConfig} — the
 * Bukkit-facing counterpart to {@link ItemConfigParser}, shared by both
 * {@code JoinItemsModule} (§5.2) and {@code MenuModule} (§5.3) so the "turn config
 * into an actual item" logic exists exactly once (see DECISIONS.md Step 4).
 *
 * <p>NOT unit-tested, unlike {@link ItemConfigParser} — every method here needs a
 * live {@link Material} lookup, {@code ItemStack#getItemMeta()} (which requires
 * {@code Bukkit.getItemFactory()}, i.e. a running server), and — for skulls —
 * {@code Bukkit.createProfile(...)}. Per this build step's own testability note,
 * this is exactly the kind of Bukkit-API-bound code that "genuinely needs a live
 * server" and is deferred to manual testing rather than forced into a JUnit test.</p>
 */
public final class ItemBuilder {

    private ItemBuilder() {
    }

    /** {@link #build(SwagHub, ItemConfig, Player, SwagHubPlaceholders)} with no placeholder resolution. */
    public static Optional<ItemStack> build(SwagHub plugin, ItemConfig config) {
        return build(plugin, config, null, null);
    }

    /**
     * Builds the item. Returns {@link Optional#empty()} (after logging one specific
     * warning naming the item id and the bad material) if {@code config}'s material
     * string doesn't resolve to a real {@link Material} — per §10.4, this never
     * throws and the caller is expected to simply skip this one item.
     *
     * @param contextPlayer who this item is being built for (used for PlaceholderAPI
     *                       resolution only) — may be {@code null} if {@code placeholders} is also {@code null}.
     * @param placeholders   resolves {@code %swaghub_...%}/PlaceholderAPI tokens in the
     *                       name/lore before MiniMessage parsing; {@code null} skips
     *                       placeholder resolution entirely (join items don't need it).
     */
    public static Optional<ItemStack> build(SwagHub plugin, ItemConfig config, Player contextPlayer,
                                             SwagHubPlaceholders placeholders) {
        Material material = Material.matchMaterial(config.getMaterialName());
        if (material == null) {
            plugin.getLogger().warning("Item '" + config.getId() + "' has an invalid material '"
                    + config.getMaterialName() + "' — skipping this item entirely.");
            return Optional.empty();
        }

        ItemStack item = new ItemStack(material, Math.max(1, config.getAmount()));
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            // No ItemMeta type exists for this material (extremely rare, e.g. AIR) — the
            // plain stack is still returned rather than treating this as a hard failure.
            return Optional.of(item);
        }

        if (config.getDisplayName() != null) {
            String name = placeholders != null ? placeholders.apply(config.getDisplayName(), contextPlayer)
                    : config.getDisplayName();
            meta.displayName(plugin.getMessageUtil().parse(name));
        }

        if (!config.getLore().isEmpty()) {
            List<Component> lore = new ArrayList<>();
            for (String line : config.getLore()) {
                String resolved = placeholders != null ? placeholders.apply(line, contextPlayer) : line;
                lore.add(plugin.getMessageUtil().parse(resolved));
            }
            meta.lore(lore);
        }

        if (config.getCustomModelData() != null) {
            // Legacy int-based custom model data (still supported in 1.21.x) — v1
            // deliberately does not touch Paper's newer component-based CMD system.
            meta.setCustomModelData(config.getCustomModelData());
        }

        if (config.isGlow()) {
            // Hidden/non-functional enchant + HIDE_ENCHANTS — the standard
            // glow-without-stats trick (§5.2). "ignoreLevelRestriction=true" lets this
            // apply to any item type, not just enchantable ones. UNBREAKING (not the
            // legacy DURABILITY name, which no longer exists on paper-api 1.21.x — see
            // DECISIONS.md Step 4) has no visible player-facing effect on non-tool items.
            meta.addEnchant(Enchantment.UNBREAKING, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }

        if (meta instanceof SkullMeta skullMeta && material == Material.PLAYER_HEAD) {
            applySkull(plugin, skullMeta, config);
        }

        item.setItemMeta(meta);
        return Optional.of(item);
    }

    /**
     * Applies a head texture via Paper's {@link PlayerProfile}/{@link ProfileProperty}
     * API — never the deprecated {@code SkullMeta#setOwner(String)}. Supports a raw
     * base64 "textures" property (arbitrary skin data), a real player name, or a real
     * player UUID, in that priority order if more than one is configured. Any failure
     * here (invalid UUID string, etc.) logs one warning naming the item id and leaves
     * the head with its default (Steve) appearance rather than failing the whole item.
     */
    private static void applySkull(SwagHub plugin, SkullMeta skullMeta, ItemConfig config) {
        String texture = config.getSkullTexture();
        String ownerUuid = config.getSkullOwnerUuid();
        String ownerName = config.getSkullOwnerName();
        if ((texture == null || texture.isBlank()) && (ownerUuid == null || ownerUuid.isBlank())
                && (ownerName == null || ownerName.isBlank())) {
            return;
        }
        try {
            PlayerProfile profile;
            if (ownerUuid != null && !ownerUuid.isBlank()) {
                profile = Bukkit.createProfile(UUID.fromString(ownerUuid.trim()));
            } else if (ownerName != null && !ownerName.isBlank()) {
                profile = Bukkit.createProfile(ownerName.trim());
            } else {
                // Texture-only (arbitrary base64 skin, no real player behind it) — a
                // random UUID is fine, it's never resolved against Mojang for this case.
                profile = Bukkit.createProfile(UUID.randomUUID());
            }
            if (texture != null && !texture.isBlank()) {
                profile.setProperty(new ProfileProperty("textures", texture.trim()));
            }
            skullMeta.setPlayerProfile(profile);
        } catch (Exception ex) {
            plugin.getLogger().warning("Item '" + config.getId() + "' has an invalid skull texture/owner"
                    + " configuration (" + ex.getMessage() + ") — using the default head appearance instead.");
        }
    }
}
