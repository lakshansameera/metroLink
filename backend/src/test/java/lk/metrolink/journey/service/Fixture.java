package lk.metrolink.journey.service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.function.UnaryOperator;
import lk.metrolink.journey.domain.Account;
import lk.metrolink.journey.domain.Journey;
import lk.metrolink.journey.domain.QRTicket;
import lk.metrolink.journey.fare.FareCalculator;
import lk.metrolink.journey.repository.AccountRepository;
import lk.metrolink.journey.repository.FareTransactionRepository;
import lk.metrolink.journey.repository.JourneyRepository;
import lk.metrolink.journey.repository.TicketRepository;
import lk.metrolink.journey.repository.memory.InMemoryAccountRepository;
import lk.metrolink.journey.repository.memory.InMemoryDatabase;
import lk.metrolink.journey.repository.memory.InMemoryFareRuleRepository;
import lk.metrolink.journey.repository.memory.InMemoryFareTransactionRepository;
import lk.metrolink.journey.repository.memory.InMemoryJourneyRepository;
import lk.metrolink.journey.repository.memory.InMemoryPassengerRepository;
import lk.metrolink.journey.repository.memory.InMemoryRouteRepository;
import lk.metrolink.journey.repository.memory.InMemoryTicketRepository;

/** Wires the services over a seeded in-memory database, the same way the real application does. */
final class Fixture {

    static final LocalDate TODAY = LocalDate.parse("2026-10-21");
    /** 09:30 in Colombo on {@link #TODAY}. */
    static final Instant MORNING = Instant.parse("2026-10-21T04:00:00Z");
    static final Instant LATER = Instant.parse("2026-10-21T04:40:00Z");

    final InMemoryDatabase db = InMemoryDatabase.seeded(TODAY);
    final TicketRepository tickets = new InMemoryTicketRepository(db);
    final JourneyRepository journeys = new InMemoryJourneyRepository(db);
    final AccountRepository accounts = new InMemoryAccountRepository(db);
    final InMemoryPassengerRepository passengers = new InMemoryPassengerRepository(db);
    final FareTransactionRepository transactions = new InMemoryFareTransactionRepository(db);
    final InMemoryRouteRepository routes = new InMemoryRouteRepository(db);
    final InMemoryFareRuleRepository fareRules = new InMemoryFareRuleRepository(db);

    final AccountService accountService;
    final QRTicketService ticketService;
    final JourneyService journeyService;

    Fixture() {
        this(real -> real);
    }

    /** Wrap the transaction repository the services use, for example to make the debit fail on purpose. */
    Fixture(UnaryOperator<FareTransactionRepository> wrapTransactions) {
        accountService = new AccountService(accounts, wrapTransactions.apply(transactions));
        ticketService = new QRTicketService(tickets, journeys);
        journeyService = new JourneyService(db, tickets, journeys, routes, fareRules, ticketService,
                accountService, new FareCalculator());
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
