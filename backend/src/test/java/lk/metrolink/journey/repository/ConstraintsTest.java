package lk.metrolink.journey.repository;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.time.Instant;
import java.time.LocalDate;
import lk.metrolink.journey.domain.Account;
import lk.metrolink.journey.domain.FareTransaction;
import lk.metrolink.journey.domain.Journey;
import lk.metrolink.journey.domain.NonResident;
import lk.metrolink.journey.domain.Passenger;
import lk.metrolink.journey.domain.PasswordHash;
import lk.metrolink.journey.domain.PermanentAccount;
import lk.metrolink.journey.domain.QRTicket;
import lk.metrolink.journey.domain.Resident;
import lk.metrolink.journey.domain.TemporaryAccount;
import lk.metrolink.journey.domain.TicketState;
import org.junit.Before;
import org.junit.Test;

/** The store refuses data that would break the rules, the way real constraints would (D2, D3, D4). Same on every store. */
public class ConstraintsTest extends OnEveryStore {

    private static final LocalDate TODAY = LocalDate.parse("2026-10-21");
    private static final Instant NOW = Instant.parse("2026-10-21T04:00:00Z");

    private UnitOfWork db;
    private AccountRepository accounts;
    private TicketRepository tickets;
    private JourneyRepository journeys;
    private FareTransactionRepository transactions;
    private PassengerRepository passengers;
    private CredentialRepository credentials;

    @Before
    public void setUp() {
        Store store = open(TODAY);
        db = store.unitOfWork();
        accounts = store.accounts();
        tickets = store.tickets();
        journeys = store.journeys();
        transactions = store.fareTransactions();
        passengers = store.passengers();
        credentials = store.credentials();
    }

    private long ticketId(String qr) {
        return db.run(() -> tickets.findByQrCode(qr).orElseThrow().getTicketId());
    }

    private void expectRefused(Runnable work) {
        try {
            db.execute(work);
            fail("expected ConstraintViolationException");
        } catch (ConstraintViolationException expected) {
            assertEquals(409, expected.status());
        }
    }

    @Test
    public void aBalanceBelowZeroIsRefused() {
        expectRefused(() -> accounts.save(new PermanentAccount(-1, 1, 1)));
    }

    @Test
    public void savingAnAccountThatDoesNotExistIsRefused() {
        expectRefused(() -> accounts.save(new PermanentAccount(10, 99, 99)));
    }

    @Test
    public void twoTicketsCannotShareAQrCode() {
        QRTicket existing = db.run(() -> tickets.findByQrCode("MLK-HAPPY01").orElseThrow());
        expectRefused(() -> tickets.insert(new QRTicket(0, "MLK-HAPPY01", 1, "R100", "S1", "S5", TODAY, 69, NOW,
                TicketState.of("ACTIVE"))));
        assertEquals(existing.getTicketId(), ticketId("MLK-HAPPY01"));
    }

    @Test
    public void aTicketForAnUnknownPassengerIsRefused() {
        expectRefused(() -> tickets.insert(new QRTicket(0, "MLK-NOBODY01", 99, "R100", "S1", "S5", TODAY, 69, NOW,
                TicketState.of("ACTIVE"))));
    }

    @Test
    public void aTicketCanHaveOnlyOneJourney() {                                  // D4
        long ticketId = ticketId("MLK-HAPPY01");
        db.execute(() -> journeys.insert(Journey.start(ticketId, "R100", "S1", NOW)));

        expectRefused(() -> journeys.insert(Journey.start(ticketId, "R100", "S1", NOW)));
        assertEquals(1, db.run(() -> journeys.list().size()).intValue());
    }

    @Test
    public void aJourneyForAnUnknownTicketIsRefused() {
        expectRefused(() -> journeys.insert(Journey.start(999, "R100", "S1", NOW)));
    }

    @Test
    public void aJourneyCanBeChargedOnlyOnce() {                                  // D2
        long ticketId = ticketId("MLK-HAPPY01");
        Journey journey = db.run(() -> journeys.insert(Journey.start(ticketId, "R100", "S1", NOW)));
        db.execute(() -> transactions.insert(FareTransaction.forJourney(journey.getJourneyId(), 1, 69, 431, NOW)));

        expectRefused(() -> transactions.insert(FareTransaction.forJourney(journey.getJourneyId(), 1, 69, 362, NOW)));
        assertEquals(1, db.run(() -> transactions.listForAccount(1).size()).intValue());
    }

    @Test
    public void aChargeForAnUnknownAccountOrJourneyIsRefused() {
        long ticketId = ticketId("MLK-HAPPY01");
        Journey journey = db.run(() -> journeys.insert(Journey.start(ticketId, "R100", "S1", NOW)));

        expectRefused(() -> transactions.insert(FareTransaction.forJourney(journey.getJourneyId(), 99, 69, 0, NOW)));
        expectRefused(() -> transactions.insert(FareTransaction.forJourney(999, 1, 69, 0, NOW)));
    }

    @Test
    public void aRefusedWriteInsideALargerUnitOfWorkUndoesTheEarlierWrites() {    // D5
        long ticketId = ticketId("MLK-HAPPY01");
        try {
            db.execute(() -> {
                Account account = accounts.findById(1).orElseThrow();
                account.deduct(69, NOW);
                accounts.save(account);
                journeys.insert(Journey.start(ticketId, "R100", "S1", NOW));
                journeys.insert(Journey.start(ticketId, "R100", "S1", NOW));     // duplicate: refused
            });
            fail("expected ConstraintViolationException");
        } catch (ConstraintViolationException expected) {
            assertTrue(expected.getMessage().contains("ticket_id"));
        }

        assertEquals(500, db.run(() -> accounts.findById(1).orElseThrow().getBalance()).longValue());
        assertEquals(0, db.run(() -> journeys.list().size()).intValue());
    }

    @Test
    public void findByReferenceFindsTheChargeOfAJourney() {
        long ticketId = ticketId("MLK-HAPPY01");
        Journey journey = db.run(() -> journeys.insert(Journey.start(ticketId, "R100", "S1", NOW)));
        db.execute(() -> transactions.insert(FareTransaction.forJourney(journey.getJourneyId(), 1, 69, 431, NOW)));

        FareTransaction found = db.run(() ->
                transactions.findByReference(FareTransaction.referenceFor(journey.getJourneyId())).orElseThrow());

        assertEquals(69, found.getAmount());
        assertEquals(431, found.getBalanceAfter());
    }


    // ---- onboarding: new customers, passwords and accounts (O2, O5, O6)

    @Test
    public void twoPassengersCannotShareAnEmail() {
        expectRefused(() -> passengers.insert(new Passenger(0, "Copy", "nimal@example.com", new Resident())));
    }

    @Test
    public void theEmailUniquenessIgnoresCase() {
        expectRefused(() -> passengers.insert(new Passenger(0, "Copy", "NIMAL@Example.com", new Resident())));
    }

    @Test
    public void aNewPassengerGetsTheNextIdAndIsReadable() {
        Passenger stored = db.run(() -> passengers.insert(new Passenger(0, "Amaya", "amaya@example.com", new Resident())));

        assertEquals(6, stored.getPassengerId());
        assertEquals("Amaya", db.run(() -> passengers.find(6)).orElseThrow().getName());
    }

    @Test
    public void anAccountForAnUnknownPassengerIsRefused() {
        expectRefused(() -> accounts.insert(new PermanentAccount(0, 99, 0)));
    }

    @Test
    public void aPassengerCannotHaveTwoAccounts() {
        expectRefused(() -> accounts.insert(new PermanentAccount(0, 1, 0)));
    }

    @Test
    public void aNewAccountWithANegativeBalanceIsRefused() {
        db.execute(() -> passengers.insert(new Passenger(0, "Amaya", "amaya@example.com", new Resident())));

        expectRefused(() -> accounts.insert(new PermanentAccount(-5, 6, 0)));
    }

    @Test
    public void aNewAccountGetsTheNextIdAndKeepsItsKind() {
        db.execute(() -> passengers.insert(new Passenger(0, "Emma", "emma.w@example.com",
                new NonResident(TODAY.plusDays(30)))));

        Account stored = db.run(() -> accounts.insert(new TemporaryAccount(0, 6, 0, TODAY.plusDays(30))));

        assertEquals(6, stored.getAccountId());
        assertEquals("TEMPORARY", stored.type());
    }

    @Test
    public void aPasswordForAnUnknownPassengerIsRefused() {
        expectRefused(() -> credentials.insert(99, PasswordHash.of("Sunny day 42", 1_000)));
    }

    @Test
    public void aPassengerCannotHaveTwoPasswords() {
        expectRefused(() -> credentials.insert(1, PasswordHash.of("Sunny day 42", 1_000)));
    }
}
