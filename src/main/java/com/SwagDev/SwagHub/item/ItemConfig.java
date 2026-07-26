package com.SwagDev.SwagHub.item;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Pure-data representation of one configured item (a join item or a menu slot/filler
 * item — §5.2/§5.3 share the exact same appearance shape: material, amount, name,
 * lore, custom model data, glow, head texture, and an action list). Deliberately has
 * ZERO Bukkit {@code Material}/{@code ItemStack} dependency — only {@link String}s
 * and numbers — so it (and {@link ItemConfigParser}) can be unit-tested without a
 * running server, mirroring the {@code SpawnLocation}/{@code SpawnLocationIO} split
 * from build step 2 (see DECISIONS.md Step 4).
 *
 * <p>Material validity (is {@code materialName} an actual {@link org.bukkit.Material}
 * constant?) is deliberately NOT checked here — that requires the live
 * {@code Material} enum and is {@link ItemBuilder}'s job at the moment the real
 * {@code ItemStack} is built, per §10.4's "log a specific warning naming the item id
 * and the problem, skip just that item" requirement.</p>
 *
 * <p>Permission is intentionally NOT a field here — per-item/per-menu permissions
 * (§9's {@code swaghub.item.<id>} / {@code swaghub.menu.<id>}) are always derived
 * from the owning config's own id, never a free-form config key, and are registered
 * as real Bukkit {@link org.bukkit.permissions.Permission}s by the owning module
 * (see DECISIONS.md Step 4, ambiguity on permission derivation).</p>
 */
public final class ItemConfig {

    private final String id;
    private final String materialName;
    private final int amount;
    private final String displayName;
    private final List<String> lore;
    private final Integer customModelData;
    private final boolean glow;
    private final String skullTexture;
    private final String skullOwnerName;
    private final String skullOwnerUuid;
    private final List<String> actions;

    public ItemConfig(String id, String materialName, int amount, String displayName, List<String> lore,
                       Integer customModelData, boolean glow, String skullTexture, String skullOwnerName,
                       String skullOwnerUuid, List<String> actions) {
        this.id = Objects.requireNonNull(id, "id");
        this.materialName = Objects.requireNonNull(materialName, "materialName");
        this.amount = amount;
        this.displayName = displayName;
        this.lore = lore == null ? Collections.emptyList() : Collections.unmodifiableList(lore);
        this.customModelData = customModelData;
        this.glow = glow;
        this.skullTexture = skullTexture;
        this.skullOwnerName = skullOwnerName;
        this.skullOwnerUuid = skullOwnerUuid;
        this.actions = actions == null ? Collections.emptyList() : Collections.unmodifiableList(actions);
    }

    public String getId() {
        return id;
    }

    /** Raw config material string, not yet resolved against {@link org.bukkit.Material}. */
    public String getMaterialName() {
        return materialName;
    }

    public int getAmount() {
        return amount;
    }

    /** Raw MiniMessage display name, or {@code null} if not configured (keeps the item's vanilla name). */
    public String getDisplayName() {
        return displayName;
    }

    /** Raw MiniMessage lore lines, never {@code null} (empty if not configured). */
    public List<String> getLore() {
        return lore;
    }

    /** {@code null} if not configured — {@link ItemBuilder} leaves the item's custom model data untouched. */
    public Integer getCustomModelData() {
        return customModelData;
    }

    public boolean isGlow() {
        return glow;
    }

    /** Base64 Mojang-signed "textures" property value, or {@code null}. */
    public String getSkullTexture() {
        return skullTexture;
    }

    /** A real player name to resolve a skin from, or {@code null}. */
    public String getSkullOwnerName() {
        return skullOwnerName;
    }

    /** A player UUID (string form) to resolve a skin from, or {@code null}. */
    public String getSkullOwnerUuid() {
        return skullOwnerUuid;
    }

    /** Raw {@code [tag] argument} action lines, never {@code null} (empty if not configured). */
    public List<String> getActions() {
        return actions;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ItemConfig other)) {
            return false;
        }
        return amount == other.amount
                && glow == other.glow
                && id.equals(other.id)
                && materialName.equals(other.materialName)
                && Objects.equals(displayName, other.displayName)
                && lore.equals(other.lore)
                && Objects.equals(customModelData, other.customModelData)
                && Objects.equals(skullTexture, other.skullTexture)
                && Objects.equals(skullOwnerName, other.skullOwnerName)
                && Objects.equals(skullOwnerUuid, other.skullOwnerUuid)
                && actions.equals(other.actions);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, materialName, amount, displayName, lore, customModelData, glow,
                skullTexture, skullOwnerName, skullOwnerUuid, actions);
    }

    @Override
    public String toString() {
        return "ItemConfig{id='" + id + "', material='" + materialName + "', amount=" + amount
                + ", displayName='" + displayName + "', customModelData=" + customModelData
                + ", glow=" + glow + "}";
    }
}
