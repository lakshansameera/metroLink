package lk.metrolink.journey.repository;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.time.Instant;
import java.time.LocalDate;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import lk.metrolink.journey.domain.Account;
import lk.metrolink.journey.domain.Passenger;
import lk.metrolink.journey.domain.PasswordHash;
import lk.metrolink.journey.domain.Resident;
import lk.metrolink.journey.repository.memory.InMemoryAccountRepository;
import lk.metrolink.journey.repository.memory.InMemoryCredentialRepository;
import lk.metrolink.journey.repository.memory.InMemoryDatabase;
import lk.metrolink.journey.repository.memory.InMemoryPassengerRepository;
import lk.metrolink.journey.repository.memory.InMemoryTicketRepository;
import org.junit.Before;
import org.junit.Test;

public class InMemoryDatabaseTest {

    private static final LocalDate TODAY = LocalDate.parse("2026-10-21");
    private static final Instant NOW = Instant.parse("2026-10-21T04:00:00Z");

    private InMemoryDatabase db;
    private AccountRepository accounts;
    private TicketRepository tickets;

    @Before
    public void setUp() {
        db = InMemoryDatabase.seeded(TODAY);
        accounts = new InMemoryAccountRepository(db);
        tickets = new InMemoryTicketRepository(db);
    }

    @Test
    public void aRepositoryCannotBeUsedOutsideAUnitOfWork() {
        try {
            accounts.findById(1);
            fail("expected IllegalStateException");
        } catch (IllegalStateException expected) {
            assertTrue(expected.getMessage().contains("UnitOfWork"));
        }
    }

    @Test
    public void whenAnExceptionEscapesEveryChangeIsRolledBack() {
        try {
            db.run(() -> {
                Account account = accounts.findById(1).orElseThrow();
                account.deduct(100, NOW);
                accounts.save(account);
                throw new IllegalStateException("boom");
            });
            fail("expected the exception to escape");
        } catch (IllegalStateException expected) {
            // fine
        }

        assertEquals(500, db.run(() -> accounts.findById(1).orElseThrow().getBalance()).longValue());
    }

    @Test
    public void whenTheWorkSucceedsTheChangesStay() {
        db.execute(() -> {
            Account account = accounts.findById(1).orElseThrow();
            account.deduct(100, NOW);
            accounts.save(account);
        });

        assertEquals(400, db.run(() -> accounts.findById(1).orElseThrow().getBalance()).longValue());
    }

    @Test
    public void aNestedUnitOfWorkJoinsTheOuterOne() {
        try {
            db.execute(() -> {
                db.execute(() -> {
                    Account account = accounts.findById(1).orElseThrow();
                    account.deduct(100, NOW);
                    accounts.save(account);
                });
                throw new IllegalStateException("outer fails after inner succeeded");
            });
            fail("expected the exception to escape");
        } catch (IllegalStateException expected) {
            // fine
        }

        assertEquals("the inner work is undone with the outer",
                500, db.run(() -> accounts.findById(1).orElseThrow().getBalance()).longValue());
    }

    @Test
    public void changingWhatYouWereGivenChangesNothingUntilYouSave() {
        db.execute(() -> {
            Account first = accounts.findById(1).orElseThrow();
            first.deduct(100, NOW);                       // not saved

            Account second = accounts.findById(1).orElseThrow();
            assertNotSame(first, second);
            assertEquals(500, second.getBalance());
        });
    }

    @Test
    public void idsAreHandedOutByTheDatabaseAndNeverReused() {
        long before = db.run(() -> tickets.list().size());
        var draft = db.run(() -> tickets.findByQrCode("MLK-HAPPY01").orElseThrow());

        long firstId = db.run(() -> tickets.insert(withQr(draft, "MLK-NEW00001")).getTicketId());
        long secondId = db.run(() -> tickets.insert(withQr(draft, "MLK-NEW00002")).getTicketId());

        assertEquals(before + 1, firstId);
        assertEquals(before + 2, secondId);
        assertEquals(before + 2, db.run(() -> tickets.list().size()).longValue());
    }

    @Test
    public void resetBringsBackTheDemoData() {
        db.execute(() -> {
            Account account = accounts.findById(1).orElseThrow();
            account.deduct(100, NOW);
            accounts.save(account);
        });

        db.reset(TODAY);

        assertEquals(500, db.run(() -> accounts.findById(1).orElseThrow().getBalance()).longValue());
    }

    @Test
    public void twoThreadsDeductingAtOnceNeverLoseAnUpdate() throws Exception {
        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        for (int i = 0; i < threads; i++) {
            pool.submit(() -> db.execute(() -> {
                Account account = accounts.findById(1).orElseThrow();
                account.deduct(10, NOW);
                accounts.save(account);
            }));
        }
        pool.shutdown();
        assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS));

        assertEquals(500 - threads * 10, db.run(() -> accounts.findById(1).orElseThrow().getBalance()).longValue());
    }

    @Test
    public void aFailedUnitOfWorkReleasesTheLock() throws Exception {
        try {
            db.execute(() -> {
                throw new IllegalStateException("boom");
            });
        } catch (IllegalStateException expected) {
            // fine
        }

        Thread other = new Thread(() -> db.execute(() -> accounts.findById(1)));
        other.start();
        other.join(2000);
        assertFalse("another thread should be able to get in", other.isAlive());
    }

    private static lk.metrolink.journey.domain.QRTicket withQr(lk.metrolink.journey.domain.QRTicket t, String qr) {
        return new lk.metrolink.journey.domain.QRTicket(0, qr, t.getPassengerId(), t.getRouteNo(),
                t.getBoardingStopId(), t.getPlannedDestinationStopId(), t.getTravelDate(), t.getQuotedFare(),
                t.getIssuedAt(), lk.metrolink.journey.domain.TicketState.of("ACTIVE"));
    }


    @Test
    public void aFailedRegistrationRollsBackEveryRowItAddedAndGivesTheIdsBack() {
        InMemoryPassengerRepository passengers = new InMemoryPassengerRepository(db);
        InMemoryCredentialRepository credentials = new InMemoryCredentialRepository(db);

        try {
            db.run(() -> {
                Passenger saved = passengers.insert(new Passenger(0, "Amaya", "amaya@example.com", new Resident()));
                credentials.insert(saved.getPassengerId(), PasswordHash.of("Sunny day 42", 1_000));
                accounts.insert(saved.openAccount(0));
                throw new IllegalStateException("something failed after three inserts");
            });
            fail("expected IllegalStateException");
        } catch (IllegalStateException expected) {
            // rolled back
        }

        db.execute(() -> {
            assertTrue(passengers.findByEmail("amaya@example.com").isEmpty());
            assertTrue(credentials.findByPassenger(6).isEmpty());
            assertTrue(accounts.findByPassenger(6).isEmpty());
        });
        Passenger next = db.run(() -> passengers.insert(new Passenger(0, "Binura", "binura@example.com", new Resident())));
        assertEquals(6, next.getPassengerId());
    }
}
