package lk.metrolink.journey;

import java.time.LocalDate;
import lk.metrolink.journey.fare.FareCalculator;
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
        UnitOfWork uow = store.unitOfWork();

        AccountService accountService = new AccountService(store.accounts(), store.fareTransactions());
        QRTicketService ticketService = new QRTicketService(store.tickets(), store.journeys());
        JourneyService journeyService = new JourneyService(uow, store.tickets(), store.journeys(), store.routes(),
                store.fareRules(), ticketService, accountService, new FareCalculator());

        this.queries = new QueryService(uow, store.passengers(), store.accounts(), store.tickets(), store.journeys(),
                store.fareTransactions(), store.routes());
        this.reader = new ReaderService(uow, journeyService, store.routes(), queries);
        this.onboarding = new OnboardingService(uow, store.passengers(), store.accounts(), store.credentials(), queries);
        this.booking = new BookingService(uow, store.accounts(), store.tickets(), store.routes(), store.fareRules(),
                new FareCalculator(), queries, QrCodes::next);
        this.testData = new TestDataService(uow, accountService, store.accounts(), queries,
                () -> store.reset(seededFor));
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
