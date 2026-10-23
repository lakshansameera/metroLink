package lk.metrolink.journey.repository.jdbc;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;
import lk.metrolink.journey.repository.DataAccessException;
import lk.metrolink.journey.repository.UnitOfWork;

/**
 * A connection to a SQL database, and the Unit of Work for it. It keeps the same promises as the in-memory database,
 * so the rest of the program cannot tell them apart.
 *
 * <ul>
 *   <li><b>One at a time.</b> One lock is held for the whole unit of work, so two scans never interleave. That is
 *       what makes "two exit scans at once produce one debit" true. Because of the lock, one connection is enough.</li>
 *   <li><b>All or nothing (rule D5).</b> The outermost unit of work is one database transaction: committed when the
 *       work finishes, rolled back when an exception escapes. Nested units join the outer one.</li>
 *   <li><b>Inside a unit of work only.</b> Repositories ask for the connection on every call, and get it only while
 *       this thread is inside a unit of work.</li>
 * </ul>
 *
 * <p>This design suits one running copy of the server. Several copies sharing one database would need a connection
 * per request and row-level locking instead of this lock.
 */
public final class JdbcDatabase implements UnitOfWork, AutoCloseable {

    private final ReentrantLock lock = new ReentrantLock();
    private final Connection connection;

    /** Opens the database. The driver for {@code url} must be on the class path. */
    public JdbcDatabase(String url, String user, String password) {
        try {
            this.connection = DriverManager.getConnection(url, user, password);
            this.connection.setAutoCommit(false);
        } catch (SQLException e) {
            throw new DataAccessException("Cannot open the database " + url + ": " + e.getMessage(), e);
        }
    }

    @Override
    public <T> T run(Supplier<T> work) {
        lock.lock();
        try {
            if (lock.getHoldCount() > 1) {
                return work.get();                 // nested: joins the outer unit of work
            }
            try {
                T result = work.get();
                connection.commit();
                return result;
            } catch (RuntimeException | Error e) {
                rollback(e);
                throw e;
            } catch (SQLException e) {              // the commit itself failed
                rollback(e);
                throw new DataAccessException("Could not save the changes: " + e.getMessage(), e);
            }
        } finally {
            lock.unlock();
        }
    }

    /** Repositories call this on every access, so using one outside a unit of work fails loudly. */
    Connection connection() {
        if (!lock.isHeldByCurrentThread()) {
            throw new IllegalStateException("Repositories must be used inside a UnitOfWork");
        }
        return connection;
    }

    private void rollback(Throwable reason) {
        try {
            connection.rollback();
        } catch (SQLException e) {
            reason.addSuppressed(e);               // keep the original failure as the one that is reported
        }
    }

    /** Closes the connection. Waits for a running unit of work to finish first. */
    @Override
    public void close() {
        lock.lock();
        try {
            connection.close();
        } catch (SQLException e) {
            throw new DataAccessException("Could not close the database: " + e.getMessage(), e);
        } finally {
            lock.unlock();
        }
    }
}
