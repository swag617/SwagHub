package com.SwagDev.SwagHub.data;

import com.SwagDev.SwagAPI.api.IDatabaseService;
import com.SwagDev.SwagAPI.model.PlayerDataModule;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * SwagHub's {@link PlayerDataModule}, registered with SwagAPI under key
 * {@code "swaghub"} (§6.4) — mirrors {@code SwagCorePlayerData}'s exact
 * load/save/table pattern, own table {@code swaghub_player_data}.
 *
 * <p><b>Step 6 additions:</b> {@link Data#vanished} (§5.10's {@code /vanish},
 * persisted so a vanished staff member's next relog doesn't un-vanish them or
 * announce their join — see {@code VanishModule}) and
 * {@link Data#playerHiderState} (§5.7's {@code [cycle-player-hider]} three-state
 * cycle, persisted so it survives relog — see {@code PlayerHiderModule}). Stored as
 * the enum's {@code name()} string rather than an ordinal int, so the table stays
 * self-describing and safe to hand-edit/inspect — see {@code SwagHubDatabase}'s
 * schema-migration notes for how these two columns are safely added to an
 * already-existing Step 5 table on upgrade.</p>
 *
 * <p><b>Player-state additions</b> ({@link Data#lastGameMode}, {@link
 * Data#wasFlying}, {@link Data#flySpeed} — see {@code PlayerStateModule}): fixes the
 * "flying in creative, log off, relog back into forced-survival and fall to your
 * death" bug caused by {@code JoinSettingsModule}'s unconditional
 * {@code set-gamemode} reset having no memory of what a player was actually doing
 * before they quit. {@link Data#lastGameMode} stores {@link
 * org.bukkit.GameMode#name()} (nullable/blank — meaning "nothing saved yet", e.g. a
 * player who has never quit since this field was added), same self-describing-string
 * convention as {@link Data#playerHiderState} above rather than an ordinal int.
 * {@link Data#flySpeed} defaults to {@code 0.1f}, Bukkit's own
 * {@code Player#setFlySpeed} default, so a player who has never touched
 * {@code /flyspeed} restores to exactly the value they'd already have without this
 * feature existing at all.</p>
 */
public class SwagHubPlayerData implements PlayerDataModule {

    public static class Data {
        public boolean scoreboardEnabled = true;
        public boolean vanished = false;
        public String playerHiderState = "ALL_VISIBLE";
        public String lastGameMode = null;
        public boolean wasFlying = false;
        public float flySpeed = 0.1f;
    }

    @Override
    public CompletableFuture<Object> load(UUID uuid, IDatabaseService db) {
        return CompletableFuture.supplyAsync(() -> {
            Data d = new Data();
            try (Connection conn = db.getConnection()) {
                String sql = "SELECT scoreboard_enabled, vanished, player_hider_state, last_game_mode, "
                        + "was_flying, fly_speed FROM swaghub_player_data WHERE uuid = ?";
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setString(1, uuid.toString());
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            d.scoreboardEnabled = rs.getInt("scoreboard_enabled") == 1;
                            d.vanished = rs.getInt("vanished") == 1;
                            String state = rs.getString("player_hider_state");
                            d.playerHiderState = state != null ? state : "ALL_VISIBLE";
                            d.lastGameMode = rs.getString("last_game_mode");
                            d.wasFlying = rs.getInt("was_flying") == 1;
                            d.flySpeed = rs.getFloat("fly_speed");
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            return d;
        });
    }

    @Override
    public CompletableFuture<Void> save(UUID uuid, Object dataObj, IDatabaseService db) {
        if (!(dataObj instanceof Data d)) {
            return CompletableFuture.completedFuture(null);
        }
        return CompletableFuture.runAsync(() -> {
            try (Connection conn = db.getConnection()) {
                String sql;
                if (db.isMySQL()) {
                    sql = "INSERT INTO swaghub_player_data (uuid, scoreboard_enabled, vanished, player_hider_state, "
                            + "last_game_mode, was_flying, fly_speed) VALUES (?, ?, ?, ?, ?, ?, ?) "
                            + "ON DUPLICATE KEY UPDATE scoreboard_enabled = VALUES(scoreboard_enabled), "
                            + "vanished = VALUES(vanished), player_hider_state = VALUES(player_hider_state), "
                            + "last_game_mode = VALUES(last_game_mode), was_flying = VALUES(was_flying), "
                            + "fly_speed = VALUES(fly_speed)";
                } else {
                    sql = "INSERT OR REPLACE INTO swaghub_player_data (uuid, scoreboard_enabled, vanished, "
                            + "player_hider_state, last_game_mode, was_flying, fly_speed) VALUES (?, ?, ?, ?, ?, ?, ?)";
                }
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setString(1, uuid.toString());
                    ps.setInt(2, d.scoreboardEnabled ? 1 : 0);
                    ps.setInt(3, d.vanished ? 1 : 0);
                    ps.setString(4, d.playerHiderState);
                    ps.setString(5, d.lastGameMode);
                    ps.setInt(6, d.wasFlying ? 1 : 0);
                    ps.setFloat(7, d.flySpeed);
                    ps.executeUpdate();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }
}
