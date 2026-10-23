package lk.metrolink.journey.log;

import java.util.function.Supplier;
import lk.metrolink.journey.repository.UnitOfWork;

/**
 * Shows the Unit of Work at work: where it begins, and whether it ends in a commit or a rollback.
 *
 * <p>This is the Decorator pattern. It has the same interface as the unit of work it wraps, so a service cannot tell
 * the difference, and it adds one thing around each call. Nothing about the store changes: the in-memory database and
 * the SQL database keep their own lock and their own all-or-nothing behaviour. A unit of work started inside another
 * joins it, so only the outermost one is a transaction. This class counts the depth for each thread, so it can say
 * which is which.
 *
 * <pre>
 *   begin    the outermost unit starts (INFO)
 *   join     one inside it joins the same transaction (DEBUG)
 *   commit   the outermost unit ended normally, so every change is kept (INFO)
 *   rollback an exception escaped, so every change is undone, and why (INFO)
 * </pre>
 *
 * <p>A {@linkplain #readOnly read-only} decorator is for code that only looks things up, such as the query service. It
 * shows begin and commit at DEBUG, because a unit that changes nothing is not worth a line each time a screen asks
 * for a list. A failure is still shown at INFO.
 */
public final class LoggedUnitOfWork implements UnitOfWork {

    /**
     * How deep this thread is. One count for all decorators, so a unit that a read-only decorator starts inside a unit
     * of a writing one is seen as nested, which is what it is.
     */
    private static final ThreadLocal<Integer> DEPTH = ThreadLocal.withInitial(() -> 0);

    private final UnitOfWork inner;
    private final Log.Level edges;

    /** A unit of work that may change things: begin and commit are shown at INFO. */
    public LoggedUnitOfWork(UnitOfWork inner) {
        this(inner, Log.Level.INFO);
    }

    private LoggedUnitOfWork(UnitOfWork inner, Log.Level edges) {
        this.inner = inner;
        this.edges = edges;
    }

    /** A unit of work that only reads: begin and commit are shown at DEBUG. */
    public static LoggedUnitOfWork readOnly(UnitOfWork inner) {
        return new LoggedUnitOfWork(inner, Log.Level.DEBUG);
    }

    @Override
    public <T> T run(Supplier<T> work) {
        int level = DEPTH.get() + 1;
        DEPTH.set(level);
        long started = System.nanoTime();
        if (level == 1) {
            edge("begin: one unit of work, so every change below is kept together or not at all");
        } else {
            Log.debug(Log.UOW, "join: a nested unit of work (depth %d) shares the outer transaction", level);
        }
        try {
            T result = inner.run(work);
            if (level == 1) {
                edge("commit: changes kept (" + (System.nanoTime() - started) / 1_000_000 + " ms)");
            }
            return result;
        } catch (RuntimeException | Error e) {
            if (level == 1) {
                Log.info(Log.UOW, "rollback: %s - every change in this unit of work is undone",
                        RepositoryTrace.describeFailure(e));
            } else {
                Log.debug(Log.UOW, "nested unit of work failed: %s", RepositoryTrace.describeFailure(e));
            }
            throw e;
        } finally {
            if (level == 1) {
                DEPTH.remove();
            } else {
                DEPTH.set(level - 1);
            }
        }
    }

    private void edge(String message) {
        if (edges == Log.Level.INFO) {
            Log.info(Log.UOW, "%s", message);
        } else {
            Log.debug(Log.UOW, "%s", message);
        }
    }
}
