package lk.metrolink.journey.repository.memory;

import java.time.LocalDate;
import lk.metrolink.journey.log.Log;
import lk.metrolink.journey.repository.AccountRepository;
import lk.metrolink.journey.repository.CredentialRepository;
import lk.metrolink.journey.repository.FareRuleRepository;
import lk.metrolink.journey.repository.FareTransactionRepository;
import lk.metrolink.journey.repository.JourneyRepository;
import lk.metrolink.journey.repository.PassengerRepository;
import lk.metrolink.journey.repository.RouteRepository;
import lk.metrolink.journey.repository.Store;
import lk.metrolink.journey.repository.TicketRepository;
import lk.metrolink.journey.repository.UnitOfWork;

/** The store that keeps everything in memory. Fast, and everything is forgotten when the program stops. */
public final class InMemoryStore implements Store {
    private final InMemoryDatabase db;
    private final RouteRepository routes;
    private final FareRuleRepository fareRules;
    private final PassengerRepository passengers;
    private final AccountRepository accounts;
    private final CredentialRepository credentials;
    private final TicketRepository tickets;
    private final JourneyRepository journeys;
    private final FareTransactionRepository fareTransactions;

    public InMemoryStore(InMemoryDatabase db) {
        this.db = db;
        this.routes = new InMemoryRouteRepository(db);
        this.fareRules = new InMemoryFareRuleRepository(db);
        this.passengers = new InMemoryPassengerRepository(db);
        this.accounts = new InMemoryAccountRepository(db);
        this.credentials = new InMemoryCredentialRepository(db);
        this.tickets = new InMemoryTicketRepository(db);
        this.journeys = new InMemoryJourneyRepository(db);
        this.fareTransactions = new InMemoryFareTransactionRepository(db);
    }

    /** A store loaded with the network and demo data, with demo tickets for {@code today}. */
    public static InMemoryStore seeded(LocalDate today) {
        Log.info(Log.APP, "InMemoryStore.seeded: demo data loaded in memory, forgotten when the program stops");
        return new InMemoryStore(InMemoryDatabase.seeded(today));
    }

    @Override public UnitOfWork unitOfWork() { return db; }
    @Override public RouteRepository routes() { return routes; }
    @Override public FareRuleRepository fareRules() { return fareRules; }
    @Override public PassengerRepository passengers() { return passengers; }
    @Override public AccountRepository accounts() { return accounts; }
    @Override public CredentialRepository credentials() { return credentials; }
    @Override public TicketRepository tickets() { return tickets; }
    @Override public JourneyRepository journeys() { return journeys; }
    @Override public FareTransactionRepository fareTransactions() { return fareTransactions; }

    @Override
    public void reset(LocalDate today) {
        db.reset(today);
    }

    @Override
    public void close() {
        // Nothing is held open.
    }
}
