package lk.metrolink.journey;

import java.time.LocalDate;
import lk.metrolink.journey.fare.FareCalculator;
import lk.metrolink.journey.log.Log;
import lk.metrolink.journey.log.LoggedUnitOfWork;
import lk.metrolink.journey.log.RepositoryTrace;
import lk.metrolink.journey.repository.AccountRepository;
import lk.metrolink.journey.repository.CredentialRepository;
import lk.metrolink.journey.repository.FareRuleRepository;
import lk.metrolink.journey.repository.FareTransactionRepository;
import lk.metrolink.journey.repository.JourneyRepository;
import lk.metrolink.journey.repository.PassengerRepository;
import lk.metrolink.journey.repository.RouteRepository;
import lk.metrolink.journey.repository.TicketRepository;
import lk.metrolink.journey.repository.Store;
import lk.metrolink.journey.repository.UnitOfWork;
import lk.metrolink.journey.repository.memory.InMemoryStore;
import lk.metrolink.journey.service.AccountService;
import lk.metrolink.journey.service.BookingService;
import lk.metrolink.journey.service.JourneyService;
import lk.metrolink.journey.service.OnboardingService;
import lk.metrolink.journey.service.QRTicketService;
import lk.metrolink.journey.service.QrCodes;
import lk.metrolink.journey.service.QueryService;
import lk.metrolink.journey.service.ReaderService;
import lk.metrolink.journey.service.TestDataService;

/**
 * The one place where objects are created and connected (the composition root). Everything else receives what it
 * needs through its constructor, so nothing else knows which store is in use. Give it an in-memory store or a
 * database store and the rest of the program works the same.
 */
public final class Container {
    public final Store store;
    public final QueryService queries;
    public final ReaderService reader;
    public final OnboardingService onboarding;
    public final BookingService booking;
    public final TestDataService testData;

    private Container(Store store, LocalDate seededFor) {
        this.store = store;

        // The console log watches the store from the outside. The unit of work and every repository are wrapped in
        // look-alikes (the Decorator and Proxy patterns) that write a line and pass the call on, so no service and no
        // store knows the log exists. With logging off, the wrappers do nothing.
        UnitOfWork uow = new LoggedUnitOfWork(store.unitOfWork());
        RouteRepository routes = RepositoryTrace.wrap(RouteRepository.class, store.routes());
        FareRuleRepository fareRules = RepositoryTrace.wrap(FareRuleRepository.class, store.fareRules());
        PassengerRepository passengers = RepositoryTrace.wrap(PassengerRepository.class, store.passengers());
        AccountRepository accounts = RepositoryTrace.wrap(AccountRepository.class, store.accounts());
        CredentialRepository credentials = RepositoryTrace.wrap(CredentialRepository.class, store.credentials());
        TicketRepository tickets = RepositoryTrace.wrap(TicketRepository.class, store.tickets());
        JourneyRepository journeys = RepositoryTrace.wrap(JourneyRepository.class, store.journeys());
        FareTransactionRepository fareTransactions =
                RepositoryTrace.wrap(FareTransactionRepository.class, store.fareTransactions());

        AccountService accountService = new AccountService(accounts, fareTransactions);
        QRTicketService ticketService = new QRTicketService(tickets, journeys);
        JourneyService journeyService = new JourneyService(uow, tickets, journeys, routes, fareRules, ticketService,
                accountService, new FareCalculator());

        // The query service only reads, so its units of work are shown at DEBUG: a screen asking for a list is not news.
        UnitOfWork readOnly = LoggedUnitOfWork.readOnly(store.unitOfWork());
        this.queries = new QueryService(readOnly, passengers, accounts, tickets, journeys, fareTransactions, routes);
        this.reader = new ReaderService(uow, journeyService, routes, queries);
        this.onboarding = new OnboardingService(uow, passengers, accounts, credentials, queries);
        this.booking = new BookingService(uow, accounts, tickets, routes, fareRules, new FareCalculator(), queries,
                QrCodes::next);
        this.testData = new TestDataService(uow, accountService, accounts, queries, () -> {
            Log.info(Log.SERVICE, "TestDataService.reset: every row is removed and the demo data is loaded again");
            store.reset(seededFor);
        });
    }

    /** A container over {@code store}. "Start over with demo data" reloads the demo data for {@code seededFor}. */
    public static Container create(Store store, LocalDate seededFor) {
        return new Container(store, seededFor);
    }

    /** A container over a fresh in-memory store whose demo tickets are for {@code today}. */
    public static Container create(LocalDate today) {
        return new Container(InMemoryStore.seeded(today), today);
    }
}
