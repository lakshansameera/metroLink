package lk.metrolink.journey.repository;

import java.time.LocalDate;

/**
 * Everywhere the application keeps its data: one object that hands out the repositories and the unit of work that
 * groups their changes.
 *
 * <p>The application is written against this interface and the repository interfaces, never against a particular
 * database. There are two stores: one that keeps everything in memory (fast, forgets on exit, used by most tests) and
 * one that keeps it in a SQL database through JDBC. {@code Container} picks one at start-up.
 */
public interface Store extends AutoCloseable {

    /** Groups repository calls into one all-or-nothing change. Repositories may only be used inside it. */
    UnitOfWork unitOfWork();

    RouteRepository routes();

    FareRuleRepository fareRules();

    PassengerRepository passengers();

    AccountRepository accounts();

    CredentialRepository credentials();

    TicketRepository tickets();

    JourneyRepository journeys();

    FareTransactionRepository fareTransactions();

    /** Throws everything away and loads the network and demo data again, with demo tickets for {@code today}. */
    void reset(LocalDate today);

    /** Lets go of anything the store holds open, such as a database connection. */
    @Override
    void close();
}
