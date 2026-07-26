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
 */
public class SwagHubPlayerData implements PlayerDataModule {

    public static class Data {
        public boolean scoreboardEnabled = true;
        public boolean vanished = false;
        public String playerHiderState = "ALL_VISIBLE";
    }

    @Override
    public CompletableFuture<Object> load(UUID uuid, IDatabaseService db) {
        return CompletableFuture.supplyAsync(() -> {
            Data d = new Data();
            try (Connection conn = db.getConnection()) {
                String sql = "SELECT scoreboard_enabled, vanished, player_hider_state FROM swaghub_player_data WHERE uuid = ?";
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setString(1, uuid.toString());
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            d.scoreboardEnabled = rs.getInt("scoreboard_enabled") == 1;
                            d.vanished = rs.getInt("vanished") == 1;
                            String state = rs.getString("player_hider_state");
                            d.playerHiderState = state != null ? state : "ALL_VISIBLE";
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
                    sql = "INSERT INTO swaghub_player_data (uuid, scoreboard_enabled, vanished, player_hider_state) "
                            + "VALUES (?, ?, ?, ?) ON DUPLICATE KEY UPDATE scoreboard_enabled = VALUES(scoreboard_enabled), "
                            + "vanished = VALUES(vanished), player_hider_state = VALUES(player_hider_state)";
                } else {
                    sql = "INSERT OR REPLACE INTO swaghub_player_data (uuid, scoreboard_enabled, vanished, player_hider_state) "
                            + "VALUES (?, ?, ?, ?)";
                }
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setString(1, uuid.toString());
                    ps.setInt(2, d.scoreboardEnabled ? 1 : 0);
                    ps.setInt(3, d.vanished ? 1 : 0);
                    ps.setString(4, d.playerHiderState);
                    ps.executeUpdate();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }
}
