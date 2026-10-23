package lk.metrolink.journey.repository.jdbc;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.time.Instant;
import java.time.LocalDate;
import java.util.TimeZone;
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
import lk.metrolink.journey.repository.ConstraintViolationException;
import lk.metrolink.journey.repository.DataAccessException;
import lk.metrolink.journey.repository.Store;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * What only a real database can promise: the data is still there after the program stops, a database that already
 * exists is never reloaded, and a bad program cannot get past the rules, because the tables refuse it too.
 */
public class JdbcStoreTest {

    private static final LocalDate DAY_ONE = LocalDate.parse("2026-10-21");
    private static final LocalDate DAY_TWO = LocalDate.parse("2026-10-22");
    private static final Instant NOW = Instant.parse("2026-10-21T04:00:00Z");

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private Store open;
    private final TimeZone originalZone = TimeZone.getDefault();

    @After
    public void closeAndRestoreZone() {
        TimeZone.setDefault(originalZone);
        if (open != null) {
            open.close();
        }
    }

    private String fileUrl() {
        return "jdbc:h2:file:" + new File(folder.getRoot(), "metrolink").getAbsolutePath();
    }

    private Store openFile(LocalDate today) {
        open = JdbcStore.open(fileUrl(), "sa", "", today);
        return open;
    }

    private void closeFile() {
        open.close();
        open = null;
    }

    private static Passenger register(Store store, String name, String email, boolean resident) {
        return store.unitOfWork().run(() -> {
            Passenger draft = new Passenger(0, name, email,
                    resident ? new Resident() : new NonResident(DAY_ONE.plusDays(30)));
            Passenger saved = store.passengers().insert(draft);
            store.credentials().insert(saved.getPassengerId(), PasswordHash.of("Sunny day 42", 1_000));
            store.accounts().insert(saved.openAccount(0));
            return saved;
        });
    }

    // ---- the first run, and every run after it

    @Test
    public void aNewDatabaseGetsTheNetworkAndTheDemoData() {
        Store store = openFile(DAY_ONE);

        store.unitOfWork().execute(() -> {
            assertEquals(5, store.passengers().list().size());
            assertEquals(10, store.tickets().list().size());
            assertTrue(store.routes().findRoute("R100").isPresent());
            assertEquals(1, store.tickets().findByQrCode("MLK-HAPPY01").orElseThrow().getTicketId());
            assertEquals(DAY_ONE, store.tickets().findByQrCode("MLK-HAPPY01").orElseThrow().getTravelDate());
        });
    }

    @Test
    public void registeredCustomersAreStillThereAfterTheProgramRestarts() {
        Store first = openFile(DAY_ONE);
        long id = register(first, "Amaya", "amaya@example.com", true).getPassengerId();
        first.unitOfWork().execute(() -> {
            Account account = first.accounts().findByPassenger(id).orElseThrow();
            account.credit(250, NOW);
            first.accounts().save(account);
        });
        closeFile();

        Store second = openFile(DAY_ONE);

        second.unitOfWork().execute(() -> {
            assertEquals("Amaya", second.passengers().findByEmail("amaya@example.com").orElseThrow().getName());
            assertEquals(250, second.accounts().findByPassenger(id).orElseThrow().getBalance());
            assertTrue(second.credentials().findByPassenger(id).orElseThrow().matches("Sunny day 42"));
        });
    }

    @Test
    public void aDatabaseThatAlreadyHasDataIsNotLoadedAgain() {
        openFile(DAY_ONE);
        closeFile();

        // The program starts a day later. Nothing may be reloaded or re-dated.
        Store second = openFile(DAY_TWO);

        second.unitOfWork().execute(() -> {
            assertEquals(5, second.passengers().list().size());
            assertEquals(10, second.tickets().list().size());
            assertEquals("the demo tickets keep the day they were created for",
                    DAY_ONE, second.tickets().findByQrCode("MLK-HAPPY01").orElseThrow().getTravelDate());
        });
    }

    @Test
    public void aTapInStaysRecordedAfterARestart() {
        Store first = openFile(DAY_ONE);
        long ticketId = first.unitOfWork().run(() -> first.tickets().findByQrCode("MLK-HAPPY01").orElseThrow().getTicketId());
        first.unitOfWork().execute(() -> first.journeys().insert(Journey.start(ticketId, "R100", "S1", NOW)));
        closeFile();

        Store second = openFile(DAY_ONE);

        Journey journey = second.unitOfWork().run(() -> second.journeys().findByTicketId(ticketId)).orElseThrow();
        assertEquals("IN_PROGRESS", journey.status());
        assertEquals(NOW, journey.getTapInTime());
    }

    @Test
    public void workThatFailsLeavesNothingBehindEvenAfterARestart() {
        Store first = openFile(DAY_ONE);
        try {
            first.unitOfWork().execute(() -> {
                register(first, "Amaya", "amaya@example.com", true);
                throw new IllegalStateException("boom");
            });
            fail("expected the failure to escape");
        } catch (IllegalStateException expected) {
            // rolled back
        }
        closeFile();

        Store second = openFile(DAY_ONE);

        assertTrue(second.unitOfWork().run(() -> second.passengers().findByEmail("amaya@example.com")).isEmpty());
        assertEquals(6, register(second, "Binura", "binura@example.com", true).getPassengerId());
    }

    @Test
    public void idsKeepCountingFromWhereTheyStoppedAfterARestart() {
        Store first = openFile(DAY_ONE);
        assertEquals(6, register(first, "Amaya", "amaya@example.com", true).getPassengerId());
        closeFile();

        Store second = openFile(DAY_ONE);

        assertEquals(7, register(second, "Binura", "binura@example.com", true).getPassengerId());
    }

    // ---- start over with demo data

    @Test
    public void startingOverEmptiesTheDatabaseAndLoadsTheDemoDataForTheNewDay() {
        Store first = openFile(DAY_ONE);
        register(first, "Amaya", "amaya@example.com", true);
        closeFile();

        Store second = openFile(DAY_ONE);
        second.reset(DAY_TWO);
        closeFile();

        Store third = openFile(DAY_ONE);
        third.unitOfWork().execute(() -> {
            assertTrue(third.passengers().findByEmail("amaya@example.com").isEmpty());
            assertEquals(5, third.passengers().list().size());
            assertEquals(DAY_TWO, third.tickets().findByQrCode("MLK-HAPPY01").orElseThrow().getTravelDate());
        });
    }

    // ---- opening and closing

    @Test
    public void aDatabaseThatCannotBeOpenedSaysWhichOne() {
        String url = "jdbc:h2:file:" + new File(folder.getRoot(), "missing/inside/metrolink").getAbsolutePath()
                + ";IFEXISTS=TRUE";
        try {
            JdbcStore.open(url, "sa", "", DAY_ONE);
            fail("expected DataAccessException");
        } catch (DataAccessException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains(url));
        }
    }

    @Test
    public void aWrongPasswordIsRefusedAtStartUp() {
        String url = fileUrl();
        JdbcStore.open(url, "sa", "right", DAY_ONE).close();

        try {
            JdbcStore.open(url, "sa", "wrong", DAY_ONE);
            fail("expected DataAccessException");
        } catch (DataAccessException expected) {
            assertTrue(expected.getMessage().contains(url));
        }
    }

    @Test
    public void aClosedStoreFailsClearlyInsteadOfReturningNothing() {
        Store store = openFile(DAY_ONE);
        closeFile();

        try {
            store.unitOfWork().run(() -> store.passengers().list());
            fail("expected DataAccessException");
        } catch (DataAccessException expected) {
            assertTrue(expected.getMessage().contains("passengers"));
        }
    }

    // ---- times and dates mean the same wherever the program runs

    @Test
    public void aMomentWrittenInOneTimeZoneIsTheSameMomentReadInAnother() {
        Instant precise = Instant.parse("2026-10-21T04:00:00.123456Z");
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Colombo"));
        Store first = openFile(DAY_ONE);
        first.unitOfWork().execute(() -> first.tickets().insert(new QRTicket(0, "MLK-ZONE0001", 1, "R100", "S1", "S5",
                DAY_ONE, 69, precise, TicketState.of("ACTIVE"))));
        closeFile();

        TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles"));
        Store second = openFile(DAY_ONE);

        QRTicket read = second.unitOfWork().run(() -> second.tickets().findByQrCode("MLK-ZONE0001")).orElseThrow();
        assertEquals(precise, read.getIssuedAt());
        assertEquals(DAY_ONE, read.getTravelDate());
    }

    // ---- the tables refuse bad data themselves, whatever the program does

    private static final String PASSENGER =
            "INSERT INTO passengers (passenger_id, name, email, email_key, residency, visa_expiry) VALUES ";

    @Test
    public void theTablesRefuseWhatTheRulesForbid() throws Exception {
        String url = "jdbc:h2:mem:schema" + System.nanoTime();
        open = JdbcStore.open(url, "sa", "", DAY_ONE);
        try (Raw raw = new Raw(url)) {
            raw.refused("23505", PASSENGER + "(50, 'Copy', 'NIMAL@example.com', 'nimal@example.com', 'RESIDENT', NULL)");
            raw.refused("23513", PASSENGER + "(50, 'Odd', 'odd@example.com', 'odd@example.com', 'ALIEN', NULL)");
            raw.refused("23513", PASSENGER + "(50, 'Res', 'res@example.com', 'res@example.com', 'RESIDENT', DATE '2027-01-01')");
            raw.refused("23513", PASSENGER + "(50, 'Tou', 'tou@example.com', 'tou@example.com', 'NON_RESIDENT', NULL)");
            raw.refused("23513", "UPDATE accounts SET balance = -1 WHERE account_id = 1");
            raw.refused("23505", "INSERT INTO accounts (account_id, passenger_id, balance) VALUES (50, 1, 0)");
            raw.refused("23506", "INSERT INTO accounts (account_id, passenger_id, balance) VALUES (50, 99, 0)");
            raw.refused("23505", "INSERT INTO tickets VALUES (50, 'MLK-HAPPY01', 1, 'R100', 'S1', 'S5', DATE '2026-10-21', 69, "
                    + "TIMESTAMP '2026-10-21 04:00:00', 'ACTIVE')");
            raw.refused("23513", "INSERT INTO tickets VALUES (50, 'MLK-BAD0001', 1, 'R100', 'S1', 'S5', DATE '2026-10-21', 69, "
                    + "TIMESTAMP '2026-10-21 04:00:00', 'LOST')");
            raw.refused("23506", "INSERT INTO tickets VALUES (50, 'MLK-BAD0002', 1, 'R100', 'S1', 'NOWHERE', DATE '2026-10-21', "
                    + "69, TIMESTAMP '2026-10-21 04:00:00', 'ACTIVE')");
            raw.refused("23503", "DELETE FROM passengers WHERE passenger_id = 1");
            raw.refused("23513", "UPDATE fare_rules SET effective_to = DATE '2000-01-01' WHERE fare_rule_id = 1");
        }
    }

    @Test
    public void theTablesAllowOneJourneyPerTicketAndOneChargePerJourney() throws Exception {
        String url = "jdbc:h2:mem:journeys" + System.nanoTime();
        open = JdbcStore.open(url, "sa", "", DAY_ONE);
        try (Raw raw = new Raw(url)) {
            raw.accepted("INSERT INTO journeys (journey_id, ticket_id, route_no, boarding_stop_id, tap_in_time, status) "
                    + "VALUES (50, 1, 'R100', 'S1', TIMESTAMP '2026-10-21 04:00:00', 'IN_PROGRESS')");
            raw.refused("23505", "INSERT INTO journeys (journey_id, ticket_id, route_no, boarding_stop_id, tap_in_time, status) "
                    + "VALUES (51, 1, 'R100', 'S1', TIMESTAMP '2026-10-21 04:00:00', 'IN_PROGRESS')");
            raw.accepted("INSERT INTO fare_transactions VALUES (50, 'FARE-50', 1, 50, 69, 431, TIMESTAMP '2026-10-21 04:40:00')");
            raw.refused("23505", "INSERT INTO fare_transactions VALUES (51, 'FARE-51', 1, 50, 69, 362, TIMESTAMP '2026-10-21 04:41:00')");
            raw.refused("23505", "INSERT INTO fare_transactions VALUES (52, 'FARE-50', 1, 50, 69, 362, TIMESTAMP '2026-10-21 04:42:00')");
        }
    }

    @Test
    public void whatTheTablesRefuseReachesTheProgramAsAConstraintViolationNamingTheRule() {
        Store store = openFile(DAY_ONE);

        try {
            store.unitOfWork().execute(() -> store.tickets().insert(new QRTicket(0, "MLK-NOBODY01", 99, "R100", "S1", "S5",
                    DAY_ONE, 69, NOW, TicketState.of("ACTIVE"))));
            fail("expected ConstraintViolationException");
        } catch (ConstraintViolationException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("FOREIGN KEY"));
        }
        try {
            store.unitOfWork().execute(() -> store.passengers().insert(new Passenger(0, "Copy", " NIMAL@example.com ", new Resident())));
            fail("expected ConstraintViolationException");
        } catch (ConstraintViolationException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("UNIQUE"));
        }
        assertFalse(store.unitOfWork().run(() -> store.passengers().list()).isEmpty());
    }

    @Test
    public void anAccountMustBeTheKindItsPassengersResidencyCallsFor() {
        Store store = openFile(DAY_ONE);
        LocalDate visa = DAY_ONE.plusDays(30);
        Passenger resident = store.unitOfWork().run(() ->
                store.passengers().insert(new Passenger(0, "Amaya", "amaya@example.com", new Resident())));
        Passenger tourist = store.unitOfWork().run(() ->
                store.passengers().insert(new Passenger(0, "Emil", "emil@example.com", new NonResident(visa))));

        refused(store, new TemporaryAccount(0, resident.getPassengerId(), 0, visa));
        refused(store, new PermanentAccount(0, tourist.getPassengerId(), 0));
        refused(store, new TemporaryAccount(0, tourist.getPassengerId(), 0, visa.plusDays(1)));

        Account permanent = store.unitOfWork().run(() ->
                store.accounts().insert(new PermanentAccount(0, resident.getPassengerId(), 0)));
        Account temporary = store.unitOfWork().run(() ->
                store.accounts().insert(new TemporaryAccount(0, tourist.getPassengerId(), 0, visa)));
        assertEquals("PERMANENT", permanent.type());
        assertEquals("TEMPORARY", temporary.type());
    }

    private static void refused(Store store, Account account) {
        try {
            store.unitOfWork().execute(() -> store.accounts().insert(account));
            fail("expected ConstraintViolationException for a " + account.type() + " account");
        } catch (ConstraintViolationException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().startsWith("accounts:"));
        }
    }

    @Test
    public void aBalanceCannotGoBelowZeroEvenIfTheDomainCheckIsSkipped() throws Exception {
        String url = "jdbc:h2:mem:balance" + System.nanoTime();
        open = JdbcStore.open(url, "sa", "", DAY_ONE);
        try (Raw raw = new Raw(url)) {
            raw.refused("23513", "UPDATE accounts SET balance = balance - 501 WHERE account_id = 1");
            raw.accepted("UPDATE accounts SET balance = balance - 500 WHERE account_id = 1");
        }
        assertEquals(0, open.unitOfWork().run(() -> open.accounts().findById(1).orElseThrow().getBalance()).longValue());
    }

    @Test
    public void aFareTransactionStoresTheMomentItWasMade() {
        Store store = openFile(DAY_ONE);
        long ticketId = store.unitOfWork().run(() -> store.tickets().findByQrCode("MLK-HAPPY01").orElseThrow().getTicketId());
        Journey journey = store.unitOfWork().run(() -> store.journeys().insert(Journey.start(ticketId, "R100", "S1", NOW)));
        Instant charged = Instant.parse("2026-10-21T04:40:00.000001Z");
        store.unitOfWork().execute(() -> store.fareTransactions().insert(
                FareTransaction.forJourney(journey.getJourneyId(), 1, 69, 431, charged)));

        FareTransaction read = store.unitOfWork().run(() ->
                store.fareTransactions().findByReference(FareTransaction.referenceFor(journey.getJourneyId()))).orElseThrow();

        assertEquals(charged, read.getCreatedAt());
    }
}
