package studios.littlesquad.welcomeflow.storage;

import org.sqlite.SQLiteDataSource;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * SQLite-backed {@link StorageProvider}.
 *
 * <p><strong>Concurrency:</strong> one connection, guarded by an intrinsic
 * lock. SQLite serialises writes anyway, and the traffic here is a handful
 * of statements per player login or step completion -- a connection pool
 * would add a dependency (HikariCP) and tuning surface for no measurable
 * gain. If the MySQL provider lands in 1.1 it can pool freely; the
 * interface makes no promises either way.
 *
 * <p>Every method assumes it is already off the main thread; see the
 * threading contract on {@link StorageProvider}.
 *
 * <p>Uses {@link SQLiteDataSource} rather than {@code DriverManager}:
 * sqlite-jdbc arrives via Paper's library loader in an isolated plugin
 * classloader, and {@code DriverManager}'s ServiceLoader-based discovery
 * runs against the system classloader, so the driver would not be found.
 */
public final class SqliteStorageProvider implements StorageProvider {

    /** Bump when adding a migration to {@link #migrate(Connection)}. */
    private static final int SCHEMA_VERSION = 1;

    private final File databaseFile;
    private final Object lock = new Object();
    private Connection connection;

    public SqliteStorageProvider(File databaseFile) {
        this.databaseFile = databaseFile;
    }

    @Override
    public String name() {
        return "SQLite";
    }

    @Override
    public void initialize() throws StorageException {
        synchronized (lock) {
            try {
                File parent = databaseFile.getParentFile();
                if (parent != null && !parent.exists() && !parent.mkdirs()) {
                    throw new StorageException("Could not create data folder: " + parent.getAbsolutePath());
                }

                SQLiteDataSource dataSource = new SQLiteDataSource();
                dataSource.setUrl("jdbc:sqlite:" + databaseFile.getAbsolutePath());
                connection = dataSource.getConnection();

                applyPragmas();
                migrate(connection);
            } catch (SQLException e) {
                throw new StorageException("Could not open the SQLite database at "
                        + databaseFile.getAbsolutePath(), e);
            }
        }
    }

    /**
     * WAL keeps readers from blocking the writer, {@code NORMAL} sync is the
     * usual durability/throughput trade for game data, and the busy timeout
     * means a momentarily locked database waits rather than throwing.
     */
    private void applyPragmas() throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA journal_mode=WAL");
            statement.execute("PRAGMA synchronous=NORMAL");
            statement.execute("PRAGMA busy_timeout=5000");
            statement.execute("PRAGMA foreign_keys=ON");
        }
    }

    // --- migrations -------------------------------------------------------

    private void migrate(Connection conn) throws SQLException {
        try (Statement statement = conn.createStatement()) {
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS schema_version (
                        version INTEGER NOT NULL
                    )""");
        }

        int current = currentSchemaVersion(conn);
        if (current >= SCHEMA_VERSION) {
            return;
        }

        // Migrations are cumulative and idempotent (IF NOT EXISTS), so a
        // fresh database and an upgrade take the same path.
        if (current < 1) {
            applyV1(conn);
        }

        writeSchemaVersion(conn, SCHEMA_VERSION);
    }

    private int currentSchemaVersion(Connection conn) throws SQLException {
        try (Statement statement = conn.createStatement();
             ResultSet rs = statement.executeQuery("SELECT version FROM schema_version LIMIT 1")) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    private void writeSchemaVersion(Connection conn, int version) throws SQLException {
        try (Statement statement = conn.createStatement()) {
            statement.execute("DELETE FROM schema_version");
        }
        try (PreparedStatement statement = conn.prepareStatement("INSERT INTO schema_version (version) VALUES (?)")) {
            statement.setInt(1, version);
            statement.executeUpdate();
        }
    }

    private void applyV1(Connection conn) throws SQLException {
        try (Statement statement = conn.createStatement()) {
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS players (
                        uuid                  TEXT    PRIMARY KEY,
                        join_count            INTEGER NOT NULL DEFAULT 0,
                        first_join_at         INTEGER NOT NULL DEFAULT 0,
                        last_seen_at          INTEGER NOT NULL DEFAULT 0,
                        first_session_seconds INTEGER NOT NULL DEFAULT 0,
                        second_join_claimed   INTEGER NOT NULL DEFAULT 0,
                        return_bonus_claims   INTEGER NOT NULL DEFAULT 0,
                        last_return_bonus_at  INTEGER NOT NULL DEFAULT 0
                    )""");

            statement.execute("""
                    CREATE TABLE IF NOT EXISTS flow_progress (
                        uuid          TEXT    NOT NULL,
                        flow_id       TEXT    NOT NULL,
                        step_index    INTEGER NOT NULL DEFAULT 0,
                        step_progress INTEGER NOT NULL DEFAULT 0,
                        state         TEXT    NOT NULL,
                        started_at    INTEGER NOT NULL,
                        updated_at    INTEGER NOT NULL,
                        PRIMARY KEY (uuid, flow_id)
                    )""");

            // Resume reads "the ACTIVE row for this player"; the funnel and
            // the stale-abandon sweep read "ACTIVE rows older than X".
            statement.execute("""
                    CREATE INDEX IF NOT EXISTS idx_flow_progress_state
                        ON flow_progress (state, updated_at)""");

            statement.execute("""
                    CREATE TABLE IF NOT EXISTS funnel (
                        flow_id TEXT    NOT NULL,
                        event   TEXT    NOT NULL,
                        count   INTEGER NOT NULL DEFAULT 0,
                        PRIMARY KEY (flow_id, event)
                    )""");
        }
    }

    @Override
    public void close() {
        synchronized (lock) {
            if (connection == null) {
                return;
            }
            try {
                connection.close();
            } catch (SQLException e) {
                // Nothing useful left to do at shutdown; swallowing beats
                // spraying a stacktrace during server stop.
            } finally {
                connection = null;
            }
        }
    }

    // --- players ----------------------------------------------------------

    @Override
    public PlayerRecord loadPlayer(UUID uuid) throws StorageException {
        String sql = """
                SELECT join_count, first_join_at, last_seen_at, first_session_seconds,
                       second_join_claimed, return_bonus_claims, last_return_bonus_at
                  FROM players WHERE uuid = ?""";
        synchronized (lock) {
            try (PreparedStatement statement = requireConnection().prepareStatement(sql)) {
                statement.setString(1, uuid.toString());
                try (ResultSet rs = statement.executeQuery()) {
                    if (!rs.next()) {
                        return new PlayerRecord(uuid);
                    }
                    return new PlayerRecord(
                            uuid,
                            rs.getInt("join_count"),
                            rs.getLong("first_join_at"),
                            rs.getLong("last_seen_at"),
                            rs.getLong("first_session_seconds"),
                            rs.getInt("second_join_claimed") != 0,
                            rs.getInt("return_bonus_claims"),
                            rs.getLong("last_return_bonus_at"));
                }
            } catch (SQLException e) {
                throw new StorageException("Could not load player data for " + uuid, e);
            }
        }
    }

    @Override
    public void savePlayer(PlayerRecord record) throws StorageException {
        String sql = """
                INSERT INTO players (uuid, join_count, first_join_at, last_seen_at, first_session_seconds,
                                     second_join_claimed, return_bonus_claims, last_return_bonus_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(uuid) DO UPDATE SET
                    join_count            = excluded.join_count,
                    first_join_at         = excluded.first_join_at,
                    last_seen_at          = excluded.last_seen_at,
                    first_session_seconds = excluded.first_session_seconds,
                    second_join_claimed   = excluded.second_join_claimed,
                    return_bonus_claims   = excluded.return_bonus_claims,
                    last_return_bonus_at  = excluded.last_return_bonus_at""";
        synchronized (lock) {
            try (PreparedStatement statement = requireConnection().prepareStatement(sql)) {
                statement.setString(1, record.uuid().toString());
                statement.setInt(2, record.joinCount());
                statement.setLong(3, record.firstJoinAt());
                statement.setLong(4, record.lastSeenAt());
                statement.setLong(5, record.firstSessionSeconds());
                statement.setInt(6, record.secondJoinClaimed() ? 1 : 0);
                statement.setInt(7, record.returnBonusClaims());
                statement.setLong(8, record.lastReturnBonusAt());
                statement.executeUpdate();
            } catch (SQLException e) {
                throw new StorageException("Could not save player data for " + record.uuid(), e);
            }
        }
    }

    // --- flow progress ----------------------------------------------------

    @Override
    public FlowProgress loadActiveProgress(UUID uuid) throws StorageException {
        String sql = """
                SELECT flow_id, step_index, step_progress, state, started_at, updated_at
                  FROM flow_progress
                 WHERE uuid = ? AND state = ?
                 ORDER BY updated_at DESC
                 LIMIT 1""";
        synchronized (lock) {
            try (PreparedStatement statement = requireConnection().prepareStatement(sql)) {
                statement.setString(1, uuid.toString());
                statement.setString(2, FlowState.ACTIVE.name());
                try (ResultSet rs = statement.executeQuery()) {
                    if (!rs.next()) {
                        return null;
                    }
                    return new FlowProgress(
                            uuid,
                            rs.getString("flow_id"),
                            rs.getInt("step_index"),
                            rs.getInt("step_progress"),
                            parseState(rs.getString("state")),
                            rs.getLong("started_at"),
                            rs.getLong("updated_at"));
                }
            } catch (SQLException e) {
                throw new StorageException("Could not load flow progress for " + uuid, e);
            }
        }
    }

    @Override
    public void saveProgress(FlowProgress progress) throws StorageException {
        String sql = """
                INSERT INTO flow_progress (uuid, flow_id, step_index, step_progress, state, started_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(uuid, flow_id) DO UPDATE SET
                    step_index    = excluded.step_index,
                    step_progress = excluded.step_progress,
                    state         = excluded.state,
                    started_at    = excluded.started_at,
                    updated_at    = excluded.updated_at""";
        synchronized (lock) {
            try (PreparedStatement statement = requireConnection().prepareStatement(sql)) {
                statement.setString(1, progress.uuid().toString());
                statement.setString(2, progress.flowId());
                statement.setInt(3, progress.stepIndex());
                statement.setInt(4, progress.stepProgress());
                statement.setString(5, progress.state().name());
                statement.setLong(6, progress.startedAt());
                statement.setLong(7, progress.updatedAt());
                statement.executeUpdate();
            } catch (SQLException e) {
                throw new StorageException("Could not save flow progress for " + progress.uuid(), e);
            }
        }
    }

    @Override
    public List<String> completedFlowIds(UUID uuid) throws StorageException {
        String sql = "SELECT flow_id FROM flow_progress WHERE uuid = ? AND state = ?";
        synchronized (lock) {
            try (PreparedStatement statement = requireConnection().prepareStatement(sql)) {
                statement.setString(1, uuid.toString());
                statement.setString(2, FlowState.COMPLETED.name());
                try (ResultSet rs = statement.executeQuery()) {
                    List<String> ids = new ArrayList<>();
                    while (rs.next()) {
                        ids.add(rs.getString("flow_id"));
                    }
                    return ids;
                }
            } catch (SQLException e) {
                throw new StorageException("Could not load completed flows for " + uuid, e);
            }
        }
    }

    @Override
    public void saveSnapshot(PlayerRecord record, FlowProgress progress) throws StorageException {
        synchronized (lock) {
            Connection conn = requireConnection();
            try {
                conn.setAutoCommit(false);
                savePlayer(record);
                try (PreparedStatement statement = conn.prepareStatement(
                        "DELETE FROM flow_progress WHERE uuid = ? AND state = 'ACTIVE'")) {
                    statement.setString(1, record.uuid().toString());
                    statement.executeUpdate();
                }
                if (progress != null) saveProgress(progress);
                conn.commit();
            } catch (SQLException | StorageException e) {
                try { conn.rollback(); } catch (SQLException rollback) { e.addSuppressed(rollback); }
                throw new StorageException("Could not save session for " + record.uuid(), e);
            } finally {
                try { conn.setAutoCommit(true); } catch (SQLException e) {
                    throw new StorageException("Could not restore transaction state", e);
                }
            }
        }
    }

    @Override
    public int markStaleProgressAbandoned(long cutoff) throws StorageException {
        return markStaleProgressAbandoned(null, cutoff);
    }

    @Override
    public int markStaleProgressAbandoned(String flowId, long cutoff) throws StorageException {
        // Two statements, one transaction: read which flows/steps are being
        // abandoned so the funnel can be credited, then flip the rows.
        String select = """
                SELECT flow_id, step_index, COUNT(*) AS total
                  FROM flow_progress
                 WHERE state = ? AND updated_at < ?
                 GROUP BY flow_id, step_index""";
        String update = "UPDATE flow_progress SET state = ? WHERE state = ? AND updated_at < ?";
        if (flowId != null) {
            String filter = "state = ? AND uuid IN (SELECT uuid FROM players WHERE last_seen_at < ?) AND flow_id = ?";
            select = "SELECT flow_id, step_index, COUNT(*) AS total FROM flow_progress WHERE "
                    + filter + " GROUP BY flow_id, step_index";
            update = "UPDATE flow_progress SET state = ? WHERE " + filter;
        }

        synchronized (lock) {
            Connection conn = requireConnection();
            try {
                conn.setAutoCommit(false);

                Map<String, Long> abandonedPerFlow = new LinkedHashMap<>();
                try (PreparedStatement statement = conn.prepareStatement(select)) {
                    statement.setString(1, FlowState.ACTIVE.name());
                    statement.setLong(2, cutoff);
                    if (flowId != null) statement.setString(3, flowId);
                    try (ResultSet rs = statement.executeQuery()) {
                        while (rs.next()) {
                            abandonedPerFlow.merge(rs.getString("flow_id"), rs.getLong("total"), Long::sum);
                        }
                    }
                }

                int changed;
                try (PreparedStatement statement = conn.prepareStatement(update)) {
                    statement.setString(1, FlowState.ABANDONED.name());
                    statement.setString(2, FlowState.ACTIVE.name());
                    statement.setLong(3, cutoff);
                    if (flowId != null) statement.setString(4, flowId);
                    changed = statement.executeUpdate();
                }

                for (Map.Entry<String, Long> entry : abandonedPerFlow.entrySet()) {
                    incrementFunnelBy(conn, entry.getKey(), FunnelEvent.ABANDONED.key(), entry.getValue());
                }

                conn.commit();
                return changed;
            } catch (SQLException e) {
                rollbackQuietly(conn);
                throw new StorageException("Could not reclassify stale flow progress", e);
            } finally {
                restoreAutoCommit(conn);
            }
        }
    }

    @Override
    public void resetPlayer(UUID uuid) throws StorageException {
        synchronized (lock) {
            Connection conn = requireConnection();
            try {
                conn.setAutoCommit(false);
                try (PreparedStatement statement = conn.prepareStatement("DELETE FROM flow_progress WHERE uuid = ?")) {
                    statement.setString(1, uuid.toString());
                    statement.executeUpdate();
                }
                try (PreparedStatement statement = conn.prepareStatement("DELETE FROM players WHERE uuid = ?")) {
                    statement.setString(1, uuid.toString());
                    statement.executeUpdate();
                }
                conn.commit();
            } catch (SQLException e) {
                rollbackQuietly(conn);
                throw new StorageException("Could not reset player data for " + uuid, e);
            } finally {
                restoreAutoCommit(conn);
            }
        }
    }

    // --- funnel -----------------------------------------------------------

    @Override
    public void incrementFunnel(String flowId, String eventKey) throws StorageException {
        synchronized (lock) {
            try {
                incrementFunnelBy(requireConnection(), flowId, eventKey, 1L);
            } catch (SQLException e) {
                throw new StorageException("Could not record funnel event " + eventKey + " for flow " + flowId, e);
            }
        }
    }

    private void incrementFunnelBy(Connection conn, String flowId, String eventKey, long delta) throws SQLException {
        String sql = """
                INSERT INTO funnel (flow_id, event, count) VALUES (?, ?, ?)
                ON CONFLICT(flow_id, event) DO UPDATE SET count = count + excluded.count""";
        try (PreparedStatement statement = conn.prepareStatement(sql)) {
            statement.setString(1, flowId);
            statement.setString(2, eventKey);
            statement.setLong(3, delta);
            statement.executeUpdate();
        }
    }

    @Override
    public Map<String, Long> funnelCounts(String flowId) throws StorageException {
        String sql = "SELECT event, count FROM funnel WHERE flow_id = ? ORDER BY event";
        synchronized (lock) {
            try (PreparedStatement statement = requireConnection().prepareStatement(sql)) {
                statement.setString(1, flowId);
                try (ResultSet rs = statement.executeQuery()) {
                    Map<String, Long> counts = new LinkedHashMap<>();
                    while (rs.next()) {
                        counts.put(rs.getString("event"), rs.getLong("count"));
                    }
                    return counts;
                }
            } catch (SQLException e) {
                throw new StorageException("Could not load funnel counters for flow " + flowId, e);
            }
        }
    }

    // --- helpers ----------------------------------------------------------

    private Connection requireConnection() throws StorageException {
        Connection current = connection;
        if (current == null) {
            throw new StorageException("Storage was used before initialize() or after close().");
        }
        return current;
    }

    /**
     * Unknown values degrade to {@link FlowState#ACTIVE} rather than
     * throwing: a row written by a future version should leave the player
     * playable, not break their login.
     */
    private static FlowState parseState(String raw) {
        if (raw == null) {
            return FlowState.ACTIVE;
        }
        try {
            return FlowState.valueOf(raw.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return FlowState.ACTIVE;
        }
    }

    private static void rollbackQuietly(Connection conn) {
        try {
            conn.rollback();
        } catch (SQLException ignored) {
            // The original exception is the one worth surfacing.
        }
    }

    private static void restoreAutoCommit(Connection conn) {
        try {
            conn.setAutoCommit(true);
        } catch (SQLException ignored) {
            // Same: don't mask the failure that got us here.
        }
    }
}
