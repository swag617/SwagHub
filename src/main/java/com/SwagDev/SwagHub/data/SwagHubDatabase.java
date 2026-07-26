package com.SwagDev.SwagHub.data;

import com.SwagDev.SwagAPI.api.IDatabaseService;
import com.SwagDev.SwagHub.SwagHub;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.logging.Level;

/**
 * Creates SwagHub's own {@code swaghub_}-prefixed table(s) — mirrors
 * {@code SwagCoreDB}'s exact pattern. Called once, synchronously, from
 * {@link SwagHub#onEnable()} right after {@code hookSwagAPI()} succeeds and before
 * any feature module is registered (§6.4), so {@link SwagHubPlayerData} always has a
 * table to read/write against once a module actually calls {@code getModuleData}/
 * {@code setModuleData}.
 *
 * <p>A {@link SQLException} here is logged (severe) but never aborts plugin startup —
 * mirrors this codebase's exception-isolation discipline (a broken database still lets
 * every non-persistence feature keep working; the per-player scoreboard toggle would
 * just always read/write its in-memory default until the underlying issue is fixed).</p>
 *
 * <p><b>Step 6 schema migration (vanish + player-hider state):</b> {@link #init()}'s
 * {@code CREATE TABLE IF NOT EXISTS} alone only creates the two new columns
 * ({@code vanished}, {@code player_hider_state}) on a genuinely FRESH install — a
 * server upgrading from a Step-5 install already has this table on disk without
 * them, and {@code CREATE TABLE IF NOT EXISTS} is a no-op against an existing table
 * (it does not add missing columns). {@link #addColumnIfMissing} runs one
 * {@code ALTER TABLE ... ADD COLUMN} per new column, each wrapped in its own
 * try/catch: on a fresh install (column already created by the CREATE TABLE above)
 * both SQLite and MySQL throw a "duplicate column" style {@link SQLException}, which
 * is silently swallowed here as the expected, harmless outcome — the two code paths
 * (fresh-install CREATE TABLE, upgrade-install ALTER TABLE) converge on the exact
 * same final schema either way. See DECISIONS.md Step 6 for the fresh-vs-upgrade
 * verification notes.</p>
 */
public class SwagHubDatabase {

    private final SwagHub plugin;
    private final IDatabaseService db;

    public SwagHubDatabase(SwagHub plugin, IDatabaseService db) {
        this.plugin = plugin;
        this.db = db;
    }

    public void init() {
        try (Connection conn = db.getConnection(); Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS swaghub_player_data (
                        uuid TEXT PRIMARY KEY,
                        scoreboard_enabled INTEGER NOT NULL DEFAULT 1,
                        vanished INTEGER NOT NULL DEFAULT 0,
                        player_hider_state TEXT NOT NULL DEFAULT 'ALL_VISIBLE'
                    )""");
            plugin.getLogger().info("Database table initialized.");
        } catch (SQLException ex) {
            plugin.getLogger().log(Level.SEVERE,
                    "Failed to initialize the 'swaghub_player_data' table — the per-player scoreboard"
                            + " toggle will not persist across sessions until this is fixed.", ex);
            return;
        }

        // Upgrade path (§11 build step 6): a pre-existing Step 5 install has this table
        // WITHOUT these two columns — CREATE TABLE IF NOT EXISTS above is a no-op against
        // it. Each ADD COLUMN attempt is independently guarded so a fresh install (where
        // the column already exists from the CREATE TABLE above) never logs anything
        // alarming for what is actually expected, harmless "duplicate column" behavior.
        addColumnIfMissing("vanished", "INTEGER NOT NULL DEFAULT 0");
        addColumnIfMissing("player_hider_state", "TEXT NOT NULL DEFAULT 'ALL_VISIBLE'");
    }

    private void addColumnIfMissing(String column, String definition) {
        try (Connection conn = db.getConnection(); Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("ALTER TABLE swaghub_player_data ADD COLUMN " + column + " " + definition);
            plugin.getLogger().info("Upgraded 'swaghub_player_data' — added missing column '" + column + "'.");
        } catch (SQLException ex) {
            // Expected on a fresh install (the column already exists from CREATE TABLE
            // above) — both SQLite and MySQL throw a "duplicate column" style
            // SQLException here, which is the harmless, expected outcome and is
            // deliberately not logged as a warning/error to avoid alarming fresh-install
            // operators for something that isn't a problem.
        }
    }
}
