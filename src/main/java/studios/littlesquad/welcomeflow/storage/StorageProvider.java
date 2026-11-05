package studios.littlesquad.welcomeflow.storage;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The persistence seam. {@link SqliteStorageProvider} is the only
 * implementation in 1.0; a MySQL provider can be added in 1.1 without
 * touching a single call site. This is the one abstraction added ahead of
 * need.
 *
 * <p><strong>Threading contract:</strong> every method here blocks on I/O
 * and must therefore be called off the main thread; wrap calls in
 * {@link studios.littlesquad.welcomeflow.util.Scheduling#supplyAsync} or
 * {@code runAsync}. Implementations are required to be safe for concurrent
 * use from Bukkit's async pool. Nothing in this interface touches the
 * Bukkit API, so implementations never need the main thread themselves.
 *
 * <p>Methods throw {@link StorageException} rather than a checked
 * {@code SQLException} to keep JDBC out of the contract.
 */
public interface StorageProvider {

    /**
     * Opens the backing store and brings the schema up to date. Safe to
     * call on an existing database; migrations are idempotent.
     */
    void initialize() throws StorageException;

    /** Flushes and releases resources. Safe to call when never opened. */
    void close();

    /** Human-readable name for logs and {@code /wf status}, e.g. "SQLite". */
    String name();

    // --- players ----------------------------------------------------------

    /**
     * @return the stored record, or a zeroed {@link PlayerRecord} for a
     *     player who has never been seen. Never {@code null}, so callers
     *     don't branch on first-join twice.
     */
    PlayerRecord loadPlayer(UUID uuid) throws StorageException;

    void savePlayer(PlayerRecord record) throws StorageException;

    // --- flow progress ----------------------------------------------------

    /**
     * @return the player's {@link FlowState#ACTIVE} progress row, or
     *     {@code null} if they are not part-way through any flow.
     */
    FlowProgress loadActiveProgress(UUID uuid) throws StorageException;

    void saveProgress(FlowProgress progress) throws StorageException;

    /** Atomically replaces the active pointer and stores the player snapshot. */
    void saveSnapshot(PlayerRecord record, FlowProgress progress) throws StorageException;

    /** Flow ids this player has finished, for {@code previous-flow-completed}. */
    List<String> completedFlowIds(UUID uuid) throws StorageException;

    /**
     * Marks every {@link FlowState#ACTIVE} row older than {@code cutoff} as
     * {@link FlowState#ABANDONED}, incrementing the matching drop-off
     * counters. Run once at startup rather than on a timer: an abandoned
     * tutorial is not time-critical, and this keeps us free of per-tick work.
     *
     * @return how many rows were reclassified
     */
    int markStaleProgressAbandoned(long cutoff) throws StorageException;

    /** Applies one flow's expiry window using the player's last online time. */
    int markStaleProgressAbandoned(String flowId, long cutoff) throws StorageException;

    /** Wipes all progress and reward flags for one player ({@code /wf reset}). */
    void resetPlayer(UUID uuid) throws StorageException;

    // --- funnel -----------------------------------------------------------

    void incrementFunnel(String flowId, String eventKey) throws StorageException;

    /** All counters for one flow, keyed by event. Empty map if none yet. */
    Map<String, Long> funnelCounts(String flowId) throws StorageException;
}
