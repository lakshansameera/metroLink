package lk.metrolink.journey.repository;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;
import lk.metrolink.journey.domain.Account;
import lk.metrolink.journey.domain.FareTransaction;
import lk.metrolink.journey.domain.Journey;
import lk.metrolink.journey.domain.Passenger;
import lk.metrolink.journey.domain.QRTicket;
import lk.metrolink.journey.domain.Resident;
import org.junit.Before;
import org.junit.Test;

/**
 * What the lists come back in, and that saving one row changes only that row. Both are easy to get wrong in SQL (a
 * missing ORDER BY or WHERE) and neither shows up until a second row exists, so they are checked here on every store.
 */
public class RepositoryQueriesTest extends OnEveryStore {

    private static final LocalDate TODAY = LocalDate.parse("2026-10-21");
    private static final Instant NOW = Instant.parse("2026-10-21T04:00:00Z");

    private UnitOfWork db;
    private PassengerRepository passengers;
    private AccountRepository accounts;
    private TicketRepository tickets;
    private JourneyRepository journeys;
    private FareTransactionRepository transactions;

    @Before
    public void setUp() {
        Store store = open(TODAY);
        db = store.unitOfWork();
        passengers = store.passengers();
        accounts = store.accounts();
        tickets = store.tickets();
        journeys = store.journeys();
        transactions = store.fareTransactions();
    }

    private long ticketId(String qr) {
        return db.run(() -> tickets.findByQrCode(qr).orElseThrow().getTicketId());
    }

    private Journey startJourney(String qr) {
        long ticketId = ticketId(qr);
        return db.run(() -> journeys.insert(Journey.start(ticketId, "R100", "S1", NOW)));
    }

    private static List<Long> ids(List<Journey> list) {
        return list.stream().map(Journey::getJourneyId).collect(Collectors.toList());
    }

    // ---- finding a customer by email

    @Test
    public void aCustomerIsFoundByEmailWhateverTheCaseAndSpacing() {
        assertEquals(1, db.run(() -> passengers.findByEmail("  NIMAL@Example.COM ")).orElseThrow().getPassengerId());
        assertTrue(db.run(() -> passengers.findByEmail("nobody@example.com")).isEmpty());
    }

    @Test
    public void anEmailIsKeptAsTypedButFoundWhateverTheCase() {
        db.execute(() -> passengers.insert(new Passenger(0, "Amaya", "Amaya@Example.COM", new Resident())));

        Passenger found = db.run(() -> passengers.findByEmail("amaya@example.com")).orElseThrow();

        assertEquals("Amaya@Example.COM", found.getEmail());
    }

    // ---- order

    @Test
    public void ticketsAreListedInTheOrderTheyWereIssued() {
        List<Long> all = db.run(() -> tickets.list()).stream().map(QRTicket::getTicketId).collect(Collectors.toList());

        assertEquals(List.of(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L), all);
    }

    @Test
    public void aCustomersTicketsAreListedInOrderAndAreOnlyTheirs() {
        List<QRTicket> nimals = db.run(() -> tickets.listForPassenger(1));

        assertEquals(6, nimals.size());
        for (int i = 1; i < nimals.size(); i++) {
            assertTrue(nimals.get(i - 1).getTicketId() < nimals.get(i).getTicketId());
        }
        assertTrue(nimals.stream().allMatch(t -> t.getPassengerId() == 1));
        assertEquals(1, db.run(() -> tickets.listForPassenger(2)).size());
    }

    @Test
    public void journeysAreListedNewestFirst() {
        Journey first = startJourney("MLK-HAPPY01");
        Journey second = startJourney("MLK-OVERTR1");
        Journey third = startJourney("MLK-PENDING");

        assertEquals(List.of(third.getJourneyId(), second.getJourneyId(), first.getJourneyId()),
                ids(db.run(() -> journeys.list())));
    }

    @Test
    public void aCustomersJourneysAreNewestFirstAndAreOnlyTheirs() {
        Journey first = startJourney("MLK-HAPPY01");
        Journey second = startJourney("MLK-OVERTR1");
        startJourney("MLK-PENDING");                                    // Sanduni's, not Nimal's

        assertEquals(List.of(second.getJourneyId(), first.getJourneyId()),
                ids(db.run(() -> journeys.listForPassenger(1))));
    }

    @Test
    public void chargesAreListedNewestFirstAndAreOnlyThatAccounts() {
        Journey first = startJourney("MLK-HAPPY01");
        Journey second = startJourney("MLK-OVERTR1");
        db.execute(() -> {
            transactions.insert(FareTransaction.forJourney(first.getJourneyId(), 1, 69, 431, NOW));
            transactions.insert(FareTransaction.forJourney(second.getJourneyId(), 1, 54, 377, NOW));
        });

        List<FareTransaction> charges = db.run(() -> transactions.listForAccount(1));

        assertEquals(List.of(second.getJourneyId(), first.getJourneyId()),
                charges.stream().map(FareTransaction::getJourneyId).collect(Collectors.toList()));
        assertTrue(db.run(() -> transactions.listForAccount(2)).isEmpty());
    }

    // ---- saving changes one row only

    @Test
    public void savingAnAccountChangesThatAccountOnly() {
        db.execute(() -> {
            Account account = accounts.findById(1).orElseThrow();
            account.deduct(100, NOW);
            accounts.save(account);
        });

        assertEquals(400, db.run(() -> accounts.findById(1).orElseThrow().getBalance()).longValue());
        assertEquals(45, db.run(() -> accounts.findById(2).orElseThrow().getBalance()).longValue());
        assertEquals(10, db.run(() -> accounts.findById(3).orElseThrow().getBalance()).longValue());
    }

    @Test
    public void savingATicketChangesThatTicketOnly() {
        db.execute(() -> {
            QRTicket ticket = tickets.findByQrCode("MLK-HAPPY01").orElseThrow();
            ticket.markUsed();
            tickets.save(ticket);
        });

        assertEquals("USED", db.run(() -> tickets.findByQrCode("MLK-HAPPY01")).orElseThrow().status());
        assertEquals("ACTIVE", db.run(() -> tickets.findByQrCode("MLK-OVERTR1")).orElseThrow().status());
        assertEquals("ACTIVE", db.run(() -> tickets.findByQrCode("MLK-PENDING")).orElseThrow().status());
    }

    @Test
    public void savingAJourneyChangesThatJourneyOnly() {
        Journey first = startJourney("MLK-HAPPY01");
        Journey second = startJourney("MLK-OVERTR1");

        db.execute(() -> {
            Journey journey = journeys.findById(first.getJourneyId()).orElseThrow();
            journey.recordExit("S5", NOW.plusSeconds(600), 69);
            journey.complete(69);
            journeys.save(journey);
        });

        Journey changed = db.run(() -> journeys.findById(first.getJourneyId())).orElseThrow();
        Journey untouched = db.run(() -> journeys.findById(second.getJourneyId())).orElseThrow();
        assertEquals("COMPLETED", changed.status());
        assertEquals("S5", changed.getExitStopId());
        assertEquals("IN_PROGRESS", untouched.status());
        assertEquals(null, untouched.getExitStopId());
        assertEquals(null, untouched.getFareCharged());
    }
}
