package com.mcxyd.purearena.storage;

import com.mcxyd.purearena.service.StatsService;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** SQLite 每日击杀 Repository。所有方法由异步持久化服务调用，连接访问串行化。 */
public final class SqliteDailyKillRepository implements AutoCloseable {

    private final Path databaseFile;
    private Connection connection;

    public SqliteDailyKillRepository(Path databaseFile) {
        this.databaseFile = databaseFile;
    }

    public synchronized void initialize() throws SQLException {
        try {
            Files.createDirectories(databaseFile.toAbsolutePath().getParent());
            connection = DriverManager.getConnection("jdbc:sqlite:" + databaseFile.toAbsolutePath());
            connection.setAutoCommit(true);
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate("""
                        CREATE TABLE IF NOT EXISTS daily_kills (
                            stats_date TEXT NOT NULL,
                            player_uuid TEXT NOT NULL,
                            player_name TEXT NOT NULL,
                            kills INTEGER NOT NULL CHECK (kills >= 0),
                            PRIMARY KEY (stats_date, player_uuid)
                        )
                        """);
                statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_daily_kills_date ON daily_kills(stats_date)");
            }
        } catch (SQLException | RuntimeException exception) {
            close();
            throw exception;
        } catch (Exception exception) {
            close();
            throw new SQLException("无法创建 SQLite 数据目录", exception);
        }
    }

    public synchronized Map<UUID, StatsService.KillEntry> load(LocalDate date) throws SQLException {
        ensureReady();
        Map<UUID, StatsService.KillEntry> result = new HashMap<>();
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT player_uuid, player_name, kills FROM daily_kills WHERE stats_date = ?")) {
            statement.setString(1, date.toString());
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    try {
                        result.put(UUID.fromString(rows.getString("player_uuid")),
                                new StatsService.KillEntry(rows.getString("player_name"), rows.getInt("kills")));
                    } catch (IllegalArgumentException ignored) {
                        // 忽略损坏的 UUID 行，避免单条历史数据阻塞整个插件启动。
                    }
                }
            }
        }
        return result;
    }

    public synchronized void increment(LocalDate date, UUID playerId, String playerName) throws SQLException {
        ensureReady();
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO daily_kills(stats_date, player_uuid, player_name, kills)
                VALUES (?, ?, ?, 1)
                ON CONFLICT(stats_date, player_uuid) DO UPDATE SET
                    player_name = excluded.player_name,
                    kills = daily_kills.kills + 1
                """)) {
            statement.setString(1, date.toString());
            statement.setString(2, playerId.toString());
            statement.setString(3, playerName);
            statement.executeUpdate();
        }
    }

    private void ensureReady() throws SQLException {
        if (connection == null || connection.isClosed()) {
            throw new SQLException("SQLite 连接尚未初始化");
        }
    }

    @Override
    public synchronized void close() {
        if (connection == null) {
            return;
        }
        try {
            connection.close();
        } catch (SQLException ignored) {
            // 关闭阶段不再抛出异常，避免阻塞插件卸载。
        } finally {
            connection = null;
        }
    }
}
