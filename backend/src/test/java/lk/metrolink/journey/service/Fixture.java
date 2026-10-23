package lk.metrolink.journey.service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;
import lk.metrolink.journey.domain.Account;
import lk.metrolink.journey.domain.Journey;
import lk.metrolink.journey.domain.QRTicket;
import lk.metrolink.journey.fare.FareCalculator;
import lk.metrolink.journey.repository.AccountRepository;
import lk.metrolink.journey.repository.FareRuleRepository;
import lk.metrolink.journey.repository.FareTransactionRepository;
import lk.metrolink.journey.repository.JourneyRepository;
import lk.metrolink.journey.repository.PassengerRepository;
import lk.metrolink.journey.repository.RouteRepository;
import lk.metrolink.journey.repository.Store;
import lk.metrolink.journey.repository.TicketRepository;
import lk.metrolink.journey.repository.UnitOfWork;

/**
 * Wires the services over a seeded store, the same way the real application does. The store is passed in, so the same
 * test can run on the in-memory store and on the SQL one.
 */
final class Fixture {

    static final LocalDate TODAY = LocalDate.parse("2026-10-21");
    /** 09:30 in Colombo on {@link #TODAY}. */
    static final Instant MORNING = Instant.parse("2026-10-21T04:00:00Z");
    static final Instant LATER = Instant.parse("2026-10-21T04:40:00Z");

    final Store store;
    final UnitOfWork db;
    final TicketRepository tickets;
    final JourneyRepository journeys;
    final AccountRepository accounts;
    final PassengerRepository passengers;
    final FareTransactionRepository transactions;
    final RouteRepository routes;
    final FareRuleRepository fareRules;

    final AccountService accountService;
    final QRTicketService ticketService;
    final JourneyService journeyService;
    final QueryService queries;
    final BookingService booking;

    /** The QR codes the next bookings will draw, in order. When it is empty, real random codes are used. */
    final List<String> queuedCodes = new ArrayList<>();

    Fixture(Store store) {
        this(store, real -> real);
    }

    /** Wrap the transaction repository the services use, for example to make the debit fail on purpose. */
    Fixture(Store store, UnaryOperator<FareTransactionRepository> wrapTransactions) {
        this.store = store;
        this.db = store.unitOfWork();
        this.tickets = store.tickets();
        this.journeys = store.journeys();
        this.accounts = store.accounts();
        this.passengers = store.passengers();
        this.transactions = store.fareTransactions();
        this.routes = store.routes();
        this.fareRules = store.fareRules();
        accountService = new AccountService(accounts, wrapTransactions.apply(transactions));
        ticketService = new QRTicketService(tickets, journeys);
        journeyService = new JourneyService(db, tickets, journeys, routes, fareRules, ticketService,
                accountService, new FareCalculator());
        queries = new QueryService(db, passengers, accounts, tickets, journeys, transactions, routes);
        booking = new BookingService(db, accounts, tickets, routes, fareRules, new FareCalculator(), queries,
                () -> queuedCodes.isEmpty() ? QrCodes.next() : queuedCodes.remove(0));
    }

    ScanResult in(String qr, String stop, Instant at) {
        return journeyService.board(new Scan(qr, stop, at));
    }

    ScanResult out(String qr, String stop, Instant at) {
        return journeyService.alight(new Scan(qr, stop, at));
    }

    long balanceOf(long passengerId) {
        return db.run(() -> accounts.findByPassenger(passengerId).orElseThrow().getBalance());
    }

    void topUp(long passengerId, long amount, Instant at) {
        db.execute(() -> {
            Account account = accounts.findByPassenger(passengerId).orElseThrow();
            account.credit(amount, at);
            accounts.save(account);
        });
    }

    QRTicket ticket(String qr) {
        return db.run(() -> tickets.findByQrCode(qr).orElseThrow());
    }

    Journey journeyOf(String qr) {
        return db.run(() -> journeys.findByTicketId(tickets.findByQrCode(qr).orElseThrow().getTicketId()).orElseThrow());
    }

    int journeyCount() {
        return db.run(() -> journeys.list().size());
    }

    int chargeCount(long passengerId) {
        return db.run(() -> transactions.listForAccount(passengerId).size());
    }
}
