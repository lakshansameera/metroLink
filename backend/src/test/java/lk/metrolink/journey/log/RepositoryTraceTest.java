package lk.metrolink.journey.log;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.lang.reflect.Proxy;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import lk.metrolink.journey.domain.Passenger;
import lk.metrolink.journey.domain.PasswordHash;
import lk.metrolink.journey.domain.QRTicket;
import lk.metrolink.journey.domain.Resident;
import lk.metrolink.journey.repository.ConstraintViolationException;
import lk.metrolink.journey.repository.CredentialRepository;
import lk.metrolink.journey.repository.OnEveryStore;
import lk.metrolink.journey.repository.PassengerRepository;
import lk.metrolink.journey.repository.Store;
import lk.metrolink.journey.repository.TicketRepository;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/** The repository proxy: what a call looks like in the log, and what it must never show. Runs on both stores. */
public class RepositoryTraceTest extends OnEveryStore {

    private static final LocalDate TODAY = LocalDate.parse("2026-10-21");

    private Store store;
    private TicketRepository tickets;

    @Before
    public void setUp() {
        store = open(TODAY);
        tickets = RepositoryTrace.wrap(TicketRepository.class, store.tickets());
    }

    @After
    public void quiet() {
        Log.reset();
    }

    private <T> T inUnit(java.util.function.Supplier<T> work) {
        return store.unitOfWork().run(work);
    }

    @Test
    public void aCallIsShownWithItsArgumentsAndWhatCameBack() {                                // L9
        try (LogCapture capture = new LogCapture(Log.Level.DEBUG)) {
            inUnit(() -> tickets.findByQrCode("MLK-HAPPY01"));

            assertEquals(capture.text(), 1, capture.lines(Log.REPO).size());
            assertTrue(capture.text(), capture.lines().get(0).endsWith(
                    "TicketRepository.findByQrCode(\"MLK-HAPPY01\") -> found ticket #1 MLK-HAPPY01 ACTIVE"));
        }
    }

    @Test
    public void aMissingRowIsSaidToBeMissing() {                                               // L9
        try (LogCapture capture = new LogCapture(Log.Level.DEBUG)) {
            Optional<QRTicket> found = inUnit(() -> tickets.findByQrCode("NOPE"));

            assertFalse(found.isPresent());
            assertTrue(capture.text(), capture.text().contains("findByQrCode(\"NOPE\") -> nothing found"));
        }
    }

    @Test
    public void aListIsShownAsHowManyRows() {                                                  // L9
        try (LogCapture capture = new LogCapture(Log.Level.DEBUG)) {
            int count = inUnit(() -> tickets.list()).size();

            assertTrue(capture.text(), capture.text().contains("TicketRepository.list() -> " + count + " rows"));
        }
    }

    @Test
    public void aCallThatReturnsNothingIsShownAsDone() {                                       // L9
        try (LogCapture capture = new LogCapture(Log.Level.DEBUG)) {
            inUnit(() -> {
                QRTicket ticket = tickets.findByQrCode("MLK-HAPPY01").orElseThrow();
                tickets.save(ticket);
                return null;
            });

            assertTrue(capture.text(), capture.text().contains("TicketRepository.save(ticket #1 MLK-HAPPY01 ACTIVE) -> done"));
        }
    }

    @Test
    public void aNewRowIsShownBeforeAndAfterItsIdIsGiven() {                                   // L9
        QRTicket draft = QRTicket.issue("MLK-NEWONE22", 1, "R100", "S1", "S5", TODAY, 69, Instant.EPOCH);
        try (LogCapture capture = new LogCapture(Log.Level.DEBUG)) {
            QRTicket stored = inUnit(() -> tickets.insert(draft));

            assertEquals(11, stored.getTicketId());
            assertTrue(capture.text(), capture.text().contains(
                    "TicketRepository.insert(ticket #0 MLK-NEWONE22 ACTIVE) -> ticket #11 MLK-NEWONE22 ACTIVE"));
        }
    }

    @Test
    public void nothingIsShownBelowDebugButTheCallStillWorks() {                               // L2
        try (LogCapture capture = new LogCapture(Log.Level.INFO)) {
            Optional<QRTicket> found = inUnit(() -> tickets.findByQrCode("MLK-HAPPY01"));

            assertTrue(found.isPresent());
            assertEquals(0, capture.lines(Log.REPO).size());
        }
    }

    @Test
    public void withTheLogOffTheProxyStillAnswersTheSameAsTheRepository() {                    // L8
        Optional<QRTicket> viaProxy = inUnit(() -> tickets.findByQrCode("MLK-HAPPY01"));
        Optional<QRTicket> direct = inUnit(() -> store.tickets().findByQrCode("MLK-HAPPY01"));

        assertEquals(direct.orElseThrow().getTicketId(), viaProxy.orElseThrow().getTicketId());
    }

    @Test
    public void aConstraintViolationReachesTheCallerAsItWasAndIsShownAsFailed() {              // L8, L9
        QRTicket twin = QRTicket.issue("MLK-HAPPY01", 1, "R100", "S1", "S5", TODAY, 69, Instant.EPOCH);
        try (LogCapture capture = new LogCapture(Log.Level.DEBUG)) {
            try {
                inUnit(() -> tickets.insert(twin));
                fail("a second ticket with the same QR code must be refused");
            } catch (ConstraintViolationException expected) {
                assertTrue(expected.getMessage(), expected.getMessage().length() > 0);
            }

            assertTrue(capture.text(), capture.text().contains("-> failed: ConstraintViolationException"));
        }
    }

    @Test
    public void anExceptionIsRethrownAsTheSameObjectNotWrappedInAReflectionException() {       // L8
        IllegalArgumentException original = new IllegalArgumentException("nope");
        TicketRepository broken = (TicketRepository) Proxy.newProxyInstance(TicketRepository.class.getClassLoader(),
                new Class<?>[] {TicketRepository.class}, (proxy, method, args) -> {
                    throw original;
                });
        TicketRepository traced = RepositoryTrace.wrap(TicketRepository.class, broken);

        for (Log.Level level : new Log.Level[] {Log.Level.OFF, Log.Level.DEBUG}) {
            try (LogCapture capture = new LogCapture(level)) {
                try {
                    traced.list();
                    fail("expected the exception");
                } catch (IllegalArgumentException e) {
                    assertSame(original, e);
                }
                assertEquals(level == Log.Level.DEBUG ? 1 : 0, capture.lines().size());
            }
        }
    }

    @Test
    public void aPasswordHashIsNeverShownNotGoingInAndNotComingOut() {                          // L4
        CredentialRepository credentials = RepositoryTrace.wrap(CredentialRepository.class, store.credentials());
        PasswordHash hash = PasswordHash.of("Sunny day 42", 1_000);
        try (LogCapture capture = new LogCapture(Log.Level.DEBUG)) {
            inUnit(() -> {
                long id = store.passengers().insert(new Passenger(0, "Amaya", "amaya@example.com", new Resident()))
                        .getPassengerId();
                credentials.insert(id, hash);
                return credentials.findByPassenger(id);
            });

            assertTrue(capture.text(), capture.text().contains("CredentialRepository.insert(6, a password hash (hidden)) -> done"));
            assertTrue(capture.text(), capture.text().contains("-> found a password hash (hidden)"));
            assertFalse(capture.text(), capture.text().contains(hash.encoded()));
            assertFalse(capture.text(), capture.text().contains("1000:"));
            assertFalse(capture.text(), capture.text().contains("Sunny"));
            assertFalse("the email of the passenger who was inserted", capture.text().contains("amaya@example.com"));
        }
    }

    @Test
    public void anEmailIsShortenedSoTheWholeAddressIsNeverShown() {                             // L4
        PassengerRepository passengers = RepositoryTrace.wrap(PassengerRepository.class, store.passengers());
        try (LogCapture capture = new LogCapture(Log.Level.DEBUG)) {
            Optional<?> found = inUnit(() -> passengers.findByEmail("nimal@example.com"));

            assertTrue(found.isPresent());
            assertTrue(capture.text(), capture.text().contains("findByEmail(\"n***@example.com\") -> found passenger #1 RESIDENT"));
            assertFalse(capture.text(), capture.text().contains("nimal@example.com"));
            assertFalse(capture.text(), capture.text().contains("Nimal"));
        }
    }

    @Test
    public void aValueOfAnUnknownKindShowsOnlyItsClassName() {                                  // L4
        assertEquals("StringBuilder", RepositoryTrace.describe(new StringBuilder("a secret")));
        assertEquals("Object", RepositoryTrace.describe(new Object()));
    }

    @Test
    public void aLongTextIsCutAndNullIsNull() {                                                 // L9
        assertEquals("\"" + "a".repeat(60) + "...\"", RepositoryTrace.describe("a".repeat(100)));
        assertEquals("\"" + "a".repeat(60) + "\"", RepositoryTrace.describe("a".repeat(60)));
        assertEquals("null", RepositoryTrace.describe(null));
    }

    @Test
    public void textWithoutAnAtSignIsNotMasked() {                                              // L4
        assertEquals("MLK-HAPPY01", RepositoryTrace.maskEmail("MLK-HAPPY01"));
        assertEquals("@odd", RepositoryTrace.maskEmail("@odd"));
        assertEquals("a***@b.lk", RepositoryTrace.maskEmail("abc@b.lk"));
    }

    @Test
    public void theProxyStillBehavesAsAnObject() {
        assertTrue(tickets.toString(), tickets.toString().startsWith("TicketRepository with console trace, over "));
        assertEquals(tickets, tickets);
        assertEquals(tickets.hashCode(), tickets.hashCode());
        assertFalse(tickets.equals(store.tickets()));
        try (LogCapture capture = new LogCapture(Log.Level.DEBUG)) {
            tickets.toString();
            tickets.hashCode();
            assertEquals("these are not repository calls", 0, capture.lines().size());
        }
    }
}
