package com.SwagDev.SwagHub.modules.teleportbow;

import com.SwagDev.SwagHub.SwagHub;
import com.SwagDev.SwagHub.compat.ServerRole;
import com.SwagDev.SwagHub.module.Module;
import com.SwagDev.SwagHub.util.HubWorldUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.persistence.PersistentDataType;

import java.util.UUID;

/**
 * §5.7 Teleport bow: permission-gated ({@code swaghub.teleportbow}, default
 * {@code op}) — teleports the shooter to the arrow's landing point.
 *
 * <p><b>"Any bow, not a special item" (resolved ambiguity):</b> §5.7's own text
 * doesn't define a special item for this — ANY bow shot by a permitted player while
 * standing in a hub world becomes a teleport arrow for that one shot. This matches
 * the rest of §5.7's "config/permission gated ambient behavior" shape (double jump
 * doesn't need a special item either) rather than inventing a whole new "custom item"
 * mechanism duplicating what {@code items.yml}/{@code ItemBuilder} already own.</p>
 *
 * <p>Identification is PDC-only, mirroring {@code JoinItemsModule}'s exact
 * precedent: the fired {@link AbstractArrow} itself is tagged (not the bow) with both
 * a marker key and the shooter's UUID, on {@link EntityShootBowEvent}. At
 * {@link ProjectileHitEvent} time, the shooter is re-resolved fresh via
 * {@code Bukkit.getPlayer(uuid)} — never a held {@link Player} reference — so a
 * shooter who disconnected mid-flight is handled safely (the arrow is simply removed
 * with no teleport attempted).</p>
 *
 * <p><b>Landing-location adjustment (resolved ambiguity):</b> for a block hit, the
 * destination is the hit block's location shifted one block in the direction of
 * {@link ProjectileHitEvent#getHitBlockFace()} (so the shooter lands ADJACENT to the
 * block face the arrow struck, never embedded inside it) with the arrow's own pitch
 * discarded in favor of the shooter's current look direction (teleporting with the
 * arrow's often-steep landing pitch would be disorienting). For an entity hit, the
 * arrow's own final location is used directly. Either way {@code %seconds%}-style
 * fall-damage/void safety is intentionally NOT added — §5.7 only asks for the
 * teleport itself, and world-protection's own fall-damage toggle (§11 step 2) already
 * covers hub worlds if an admin wants that.</p>
 */
public class TeleportBowModule extends Module implements Listener {

    private static final String PERMISSION = "swaghub.teleportbow";

    private final NamespacedKey markerKey;
    private final NamespacedKey shooterKey;

    public TeleportBowModule(SwagHub plugin) {
        super(plugin, "teleport-bow");
        this.markerKey = new NamespacedKey(plugin, "teleport_bow_arrow");
        this.shooterKey = new NamespacedKey(plugin, "teleport_bow_shooter");
    }

    @Override
    public boolean isEnabledByDefault() {
        return plugin.getCompatibilityManager().getServerRole() == ServerRole.HUB;
    }

    @Override
    protected void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @Override
    protected void onDisable() {
        HandlerList.unregisterAll(this);
    }

    @Override
    protected void onReload() {
        // No settings of its own beyond the permission check — nothing to re-read.
    }

    @EventHandler(ignoreCancelled = true)
    public void onShoot(EntityShootBowEvent event) {
        if (!(event.getEntity() instanceof Player shooter)) {
            return;
        }
        if (!(event.getProjectile() instanceof AbstractArrow arrow)) {
            return;
        }
        if (!shooter.hasPermission(PERMISSION)) {
            return;
        }
        if (!HubWorldUtil.isHubWorld(plugin, shooter.getWorld())) {
            return;
        }

        arrow.getPersistentDataContainer().set(markerKey, PersistentDataType.BYTE, (byte) 1);
        arrow.getPersistentDataContainer().set(shooterKey, PersistentDataType.STRING, shooter.getUniqueId().toString());
    }

    @EventHandler(ignoreCancelled = true)
    public void onHit(ProjectileHitEvent event) {
        Entity projectile = event.getEntity();
        if (!(projectile instanceof AbstractArrow arrow)) {
            return;
        }
        if (!arrow.getPersistentDataContainer().has(markerKey, PersistentDataType.BYTE)) {
            return;
        }

        String shooterUuidRaw = arrow.getPersistentDataContainer().get(shooterKey, PersistentDataType.STRING);
        arrow.remove();

        if (shooterUuidRaw == null) {
            return;
        }
        Player shooter;
        try {
            shooter = Bukkit.getPlayer(UUID.fromString(shooterUuidRaw));
        } catch (IllegalArgumentException ex) {
            return;
        }
        if (shooter == null || !shooter.isOnline()) {
            return; // shooter disconnected mid-flight — nothing to teleport
        }

        Location destination = resolveDestination(event, shooter);
        if (destination != null) {
            shooter.teleport(destination);
            shooter.getWorld().playSound(destination, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);
        }
    }

    private Location resolveDestination(ProjectileHitEvent event, Player shooter) {
        Location arrowLocation = event.getEntity().getLocation();

        Block hitBlock = event.getHitBlock();
        Location destination;
        if (hitBlock != null) {
            destination = hitBlock.getLocation().add(0.5, 1.0, 0.5);
            if (event.getHitBlockFace() != null) {
                destination.add(event.getHitBlockFace().getDirection().multiply(0.5));
            }
        } else {
            destination = arrowLocation;
        }

        destination.setYaw(shooter.getLocation().getYaw());
        destination.setPitch(shooter.getLocation().getPitch());
        return destination;
    }
}
