package lk.metrolink.journey.repository.memory;

import java.time.LocalDate;
import java.util.TreeMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;
import lk.metrolink.journey.domain.Account;
import lk.metrolink.journey.domain.FareRule;
import lk.metrolink.journey.domain.FareTransaction;
import lk.metrolink.journey.domain.Journey;
import lk.metrolink.journey.domain.Passenger;
import lk.metrolink.journey.domain.QRTicket;
import lk.metrolink.journey.domain.Route;
import lk.metrolink.journey.domain.Stop;
import lk.metrolink.journey.repository.UnitOfWork;

/**
 * A small database that lives in memory, and the Unit of Work for it.
 *
 * <ul>
 *   <li><b>One at a time.</b> A single lock is held for the whole unit of work, so two scans never
 *       interleave. That is what makes "two exit scans at once produce one debit" true.</li>
 *   <li><b>All or nothing (rule D5).</b> When the outermost unit of work starts, the tables are
 *       snapshotted. If an exception escapes, the snapshot is put back, so a failed scan leaves
 *       nothing half-written.</li>
 *   <li><b>Copies only.</b> The repositories hand out copies and store copies, so a snapshot only
 *       needs to copy the tables, not the objects inside them.</li>
 * </ul>
 */
public final class InMemoryDatabase implements UnitOfWork {

    /** Every table and id counter. Package-private: only the repositories in this package touch it. */
    static final class Tables {
        TreeMap<String, Stop> stops = new TreeMap<>();
        TreeMap<String, Route> routes = new TreeMap<>();
        TreeMap<Long, FareRule> fareRules = new TreeMap<>();
        TreeMap<Long, Passenger> passengers = new TreeMap<>();
        TreeMap<Long, Account> accounts = new TreeMap<>();
        TreeMap<Long, QRTicket> tickets = new TreeMap<>();
        TreeMap<Long, Journey> journeys = new TreeMap<>();
        TreeMap<Long, FareTransaction> fareTransactions = new TreeMap<>();
        long nextTicketId = 1;
        long nextJourneyId = 1;
        long nextTransactionId = 1;

        Tables copy() {
            Tables t = new Tables();
            t.stops = new TreeMap<>(stops);
            t.routes = new TreeMap<>(routes);
            t.fareRules = new TreeMap<>(fareRules);
            t.passengers = new TreeMap<>(passengers);
            t.accounts = new TreeMap<>(accounts);
            t.tickets = new TreeMap<>(tickets);
            t.journeys = new TreeMap<>(journeys);
            t.fareTransactions = new TreeMap<>(fareTransactions);
            t.nextTicketId = nextTicketId;
            t.nextJourneyId = nextJourneyId;
            t.nextTransactionId = nextTransactionId;
            return t;
        }
    }

    private final ReentrantLock lock = new ReentrantLock();
    private Tables tables = new Tables();

    /** An empty database. */
    public InMemoryDatabase() {
    }

    /** A database loaded with the network, prices and demo passengers, with tickets for {@code today}. */
    public static InMemoryDatabase seeded(LocalDate today) {
        InMemoryDatabase db = new InMemoryDatabase();
        db.reset(today);
        return db;
    }

    @Override
    public <T> T run(Supplier<T> work) {
        lock.lock();
        try {
            if (lock.getHoldCount() > 1) {
                return work.get();                 // nested: joins the outer unit of work
            }
            Tables snapshot = tables.copy();
            try {
                return work.get();
            } catch (RuntimeException | Error e) {
                tables = snapshot;                 // roll back
                throw e;
            }
        } finally {
            lock.unlock();
        }
    }

    /** Throws everything away and loads the demo data again. */
    public void reset(LocalDate today) {
        run(() -> {
            Tables fresh = new Tables();
            DemoData.load(fresh, today);
            tables = fresh;
            return null;
        });
    }

    /** Repositories call this on every access, so using one outside a unit of work fails loudly. */
    Tables tables() {
        if (!lock.isHeldByCurrentThread()) {
            throw new IllegalStateException("Repositories must be used inside a UnitOfWork");
        }
        return tables;
    }
}
