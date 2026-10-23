package lk.metrolink.journey;

import java.time.LocalDate;
import lk.metrolink.journey.fare.FareCalculator;
import lk.metrolink.journey.repository.memory.InMemoryAccountRepository;
import lk.metrolink.journey.repository.memory.InMemoryCredentialRepository;
import lk.metrolink.journey.repository.memory.InMemoryDatabase;
import lk.metrolink.journey.repository.memory.InMemoryFareRuleRepository;
import lk.metrolink.journey.repository.memory.InMemoryFareTransactionRepository;
import lk.metrolink.journey.repository.memory.InMemoryJourneyRepository;
import lk.metrolink.journey.repository.memory.InMemoryPassengerRepository;
import lk.metrolink.journey.repository.memory.InMemoryRouteRepository;
import lk.metrolink.journey.repository.memory.InMemoryTicketRepository;
import lk.metrolink.journey.service.AccountService;
import lk.metrolink.journey.service.JourneyService;
import lk.metrolink.journey.service.OnboardingService;
import lk.metrolink.journey.service.QRTicketService;
import lk.metrolink.journey.service.QueryService;
import lk.metrolink.journey.service.ReaderService;
import lk.metrolink.journey.service.TestDataService;

/**
 * The one place where objects are created and connected (the composition root). Everything else receives what it
 * needs through its constructor, so nothing else knows which store is in use. To use a real database, write the
 * repository interfaces for it and build them here instead of the in-memory ones.
 */
public final class Container {
    public final InMemoryDatabase db;
    public final QueryService queries;
    public final ReaderService reader;
    public final OnboardingService onboarding;
    public final TestDataService testData;

    private final LocalDate seededFor;

    private Container(InMemoryDatabase db, LocalDate seededFor) {
        this.db = db;
        this.seededFor = seededFor;

        InMemoryRouteRepository routes = new InMemoryRouteRepository(db);
        InMemoryFareRuleRepository fareRules = new InMemoryFareRuleRepository(db);
        InMemoryPassengerRepository passengers = new InMemoryPassengerRepository(db);
        InMemoryAccountRepository accounts = new InMemoryAccountRepository(db);
        InMemoryFareTransactionRepository transactions = new InMemoryFareTransactionRepository(db);
        InMemoryTicketRepository tickets = new InMemoryTicketRepository(db);
        InMemoryJourneyRepository journeys = new InMemoryJourneyRepository(db);
        InMemoryCredentialRepository credentials = new InMemoryCredentialRepository(db);

        AccountService accountService = new AccountService(accounts, transactions);
        QRTicketService ticketService = new QRTicketService(tickets, journeys);
        JourneyService journeyService = new JourneyService(db, tickets, journeys, routes, fareRules, ticketService,
                accountService, new FareCalculator());

        this.queries = new QueryService(db, passengers, accounts, tickets, journeys, transactions, routes);
        this.reader = new ReaderService(db, journeyService, routes, queries);
        this.onboarding = new OnboardingService(db, passengers, accounts, credentials, queries);
        this.testData = new TestDataService(db, accountService, accounts, queries, () -> db.reset(seededFor));
    }

    /** A container over a fresh in-memory database whose demo tickets are for {@code today}. */
    public static Container create(LocalDate today) {
        return new Container(InMemoryDatabase.seeded(today), today);
    }
}
