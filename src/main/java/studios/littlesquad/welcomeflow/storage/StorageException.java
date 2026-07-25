package studios.littlesquad.welcomeflow.storage;

/**
 * Wraps the checked {@link java.sql.SQLException} so the
 * {@link StorageProvider} contract stays free of JDBC types and a MySQL
 * provider can be dropped in for 1.1 without changing call sites.
 *
 * <p>Callers are expected to catch this and log an actionable message --
 * per the quality bar, a raw stacktrace must never be the only thing an
 * owner sees in console.
 */
public class StorageException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public StorageException(String message, Throwable cause) {
        super(message, cause);
    }

    public StorageException(String message) {
        super(message);
    }
}
