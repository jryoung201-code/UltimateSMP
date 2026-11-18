package shop.ultimatesmp.shop;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Properties;

final class DiscordDatabase {
    private final String url;

    DiscordDatabase(Path file) {
        url = "jdbc:sqlite:" + file.toAbsolutePath();
    }

    Connection connect() throws SQLException {
        var properties = new Properties();
        properties.setProperty("busy_timeout", "5000");
        properties.setProperty("transaction_mode", "IMMEDIATE");
        return new org.sqlite.JDBC().connect(url, properties);
    }

    void initialize() throws SQLException {
        try (var db = connect(); var statement = db.createStatement()) {
            statement.execute("CREATE TABLE IF NOT EXISTS ultimate_discord_links (discord_id TEXT PRIMARY KEY, player_id TEXT NOT NULL UNIQUE)");
            statement.execute("CREATE TABLE IF NOT EXISTS ultimate_discord_codes (discord_id TEXT PRIMARY KEY, token_hash TEXT NOT NULL UNIQUE, expires INTEGER NOT NULL)");
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS ultimate_discord_giveaways (
                      id TEXT PRIMARY KEY, guild_id TEXT NOT NULL, channel_id TEXT NOT NULL,
                      message_id TEXT NOT NULL, prize TEXT NOT NULL, quantity INTEGER NOT NULL,
                      winners INTEGER NOT NULL, ends INTEGER NOT NULL, result TEXT,
                      announced INTEGER NOT NULL DEFAULT 0)
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS ultimate_discord_entries (
                      giveaway_id TEXT NOT NULL, discord_id TEXT NOT NULL,
                      PRIMARY KEY(giveaway_id, discord_id))
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS giveaway_delivery_queue (
                      giveaway_id TEXT NOT NULL, source TEXT PRIMARY KEY, player_id TEXT NOT NULL,
                      prize TEXT NOT NULL, quantity INTEGER NOT NULL, drawn_at INTEGER NOT NULL)
                    """);
        }
    }
}
