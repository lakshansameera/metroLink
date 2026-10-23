package lk.metrolink.journey.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import lk.metrolink.journey.Container;
import lk.metrolink.journey.domain.BookingRequest;
import lk.metrolink.journey.domain.DomainException;
import lk.metrolink.journey.domain.InvalidBookingException;
import lk.metrolink.journey.domain.InvalidRegistrationException;
import lk.metrolink.journey.domain.OnboardingRequest;
import lk.metrolink.journey.domain.ResidencyKind;
import lk.metrolink.journey.log.Log;
import lk.metrolink.journey.log.LogCapture;
import lk.metrolink.journey.repository.OnEveryStore;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * What the console tells you while the program works (rules L1 to L9 in docs/rules.md). Each test runs a real
 * scenario through the same wiring the application uses and reads the lines it printed, so the log is proved to follow
 * the states and the classes in the order they really act. Runs on the in-memory store and on the SQL store.
 */
public class ConsoleTraceTest extends OnEveryStore {

    private static final LocalDate TODAY = Fixture.TODAY;
    private static final Instant MORNING = Fixture.MORNING;
    private static final Instant LATER = Fixture.LATER;

    private Container app;

    @Before
    public void setUp() {
        app = Container.create(open(TODAY), TODAY);
    }

    @After
    public void quiet() {
        Log.reset();
    }

    private ReaderDisplay tap(String qr, ScanDirection direction, String stop) {
        return app.reader.scan(new ScanRequest(qr, direction, stop, direction == ScanDirection.IN ? MORNING : LATER));
    }

    private static OnboardingRequest resident(String name, String email) {
        return new OnboardingRequest(name, email, "a long secret password", ResidencyKind.RESIDENT, null);
    }

    // ---- a journey, class by class (L6, L7)

    @Test
    public void tappingInShowsTheClassesInTheOrderTheyAct() {                                    // L6, L7
        try (LogCapture capture = new LogCapture(Log.Level.INFO)) {
            tap("MLK-HAPPY01", ScanDirection.IN, "S1");

            capture.assertInOrder(
                    "UOW      begin:",
                    "ReaderService.scan (the Facade): IN tap for ticket MLK-HAPPY01 at stop S1",
                    "JourneyService.board: ticket MLK-HAPPY01 tapped in at stop S1",
                    "STATE    Ticket #1 MLK-HAPPY01 is ACTIVE: ActiveState.refusalReason(travel date 2026-10-21,"
                            + " today 2026-10-21) -> usable",
                    "QRTicketService.validate: ticket #1 may start a journey",
                    "STRATEGY FareCalculator: route R100 S1 to S5 is 10.8 km; fare rule #2 in force on 2026-10-21 is"
                            + " DISTANCE -> DistanceFareStrategy",
                    "STRATEGY DistanceFareStrategy: base LKR 20 + LKR 4.5 per km x 10.8 km = LKR 68.60, rounded up to"
                            + " LKR 69",
                    "ACCOUNT  Account #1 (PermanentAccount): isUsableAt -> true, balance LKR 500 against LKR 69"
                            + " -> can pay",
                    "FACTORY  Journey.start: new journey for ticket #1 at stop S1, starts in the IN_PROGRESS state",
                    "JourneyService.board: JOURNEY_STARTED, needs LKR 69, balance LKR 500",
                    "ReaderService.display: JOURNEY_STARTED -> tone SUCCESS",
                    "UOW      commit: changes kept");
        }
    }

    @Test
    public void tappingOutShowsEachStateChangeAndThePaymentBetweenThem() {                       // L6, L7
        tap("MLK-HAPPY01", ScanDirection.IN, "S1");
        try (LogCapture capture = new LogCapture(Log.Level.INFO)) {
            tap("MLK-HAPPY01", ScanDirection.OUT, "S5");

            capture.assertInOrder(
                    "UOW      begin:",
                    "JourneyService.alight: ticket MLK-HAPPY01 tapped out at stop S5",
                    "STATE    Journey #1: IN_PROGRESS -> IN_PROGRESS (InProgressState.recordExit: left at S5,"
                            + " fare due LKR 69)",
                    "JourneyService.attemptSettlement: journey #1 owes LKR 69",
                    "ACCOUNT  Account #1 (PermanentAccount): isUsableAt -> true, balance LKR 500 against LKR 69"
                            + " -> can pay",
                    "ACCOUNT  AccountService.debitFare: FARE-1 took LKR 69 from account #1, balance LKR 500 -> LKR 431",
                    "STATE    Journey #1: IN_PROGRESS -> COMPLETED (InProgressState.complete: LKR 69 charged)",
                    "STATE    Ticket #1 MLK-HAPPY01: ACTIVE -> USED (ActiveState.markUsed)",
                    "JourneyService.alight: JOURNEY_COMPLETED, fare due LKR 69, charged LKR 69, balance LKR 431",
                    "UOW      commit: changes kept");
        }
    }

    @Test
    public void aUsedTicketIsRefusedByItsOwnStateAndNoFareIsWorkedOut() {                        // L6
        tap("MLK-HAPPY01", ScanDirection.IN, "S1");
        tap("MLK-HAPPY01", ScanDirection.OUT, "S5");
        try (LogCapture capture = new LogCapture(Log.Level.INFO)) {
            tap("MLK-HAPPY01", ScanDirection.IN, "S1");

            capture.assertInOrder(
                    "STATE    Ticket #1 MLK-HAPPY01 is USED: UsedState.refusalReason(travel date 2026-10-21,"
                            + " today 2026-10-21) -> TICKET_ALREADY_USED",
                    "QRTicketService.validate: the ticket refused itself -> TICKET_ALREADY_USED",
                    "JourneyService.board: REJECTED (TICKET_ALREADY_USED)",
                    "ReaderService.display: REJECTED -> tone ERROR");
            assertTrue("a refused ticket is not priced", capture.lines(Log.STRATEGY).isEmpty());
            assertTrue("and no journey is created", capture.lines(Log.FACTORY).isEmpty());
            assertTrue("a refusal at the gate is not a warning", capture.lines().stream().noneMatch(l -> l.contains(" WARN ")));
        }
    }

    @Test
    public void aTicketForAnotherDayIsRefusedWithTheTwoDatesInTheLine() {                        // L6
        try (LogCapture capture = new LogCapture(Log.Level.INFO)) {
            tap("MLK-OLD0001", ScanDirection.IN, "S1");
            tap("MLK-FUTURE1", ScanDirection.IN, "S1");

            capture.assertInOrder(
                    "ActiveState.refusalReason(travel date 2026-10-20, today 2026-10-21) -> TICKET_EXPIRED",
                    "ActiveState.refusalReason(travel date 2026-10-22, today 2026-10-21) -> WRONG_TRAVEL_DATE");
        }
    }

    @Test
    public void aCancelledTicketIsRefusedByTheCancelledState() {                                 // L6
        try (LogCapture capture = new LogCapture(Log.Level.INFO)) {
            tap("MLK-CANCEL01", ScanDirection.IN, "S1");

            capture.assertInOrder("CancelledState.refusalReason", "TICKET_CANCELLED");
        }
    }

    @Test
    public void notEnoughCreditStopsTheJourneyBeforeItStartsAndSaysHowMuchIsMissing() {          // L6
        try (LogCapture capture = new LogCapture(Log.Level.INFO)) {
            tap("MLK-NOFUNDS", ScanDirection.IN, "S1");

            capture.assertInOrder(
                    "ACCOUNT  Account #3 (PermanentAccount): isUsableAt -> true, balance LKR 10 against LKR 40"
                            + " -> cannot pay",
                    "JourneyService.board: REJECTED (INSUFFICIENT_CREDIT), needs LKR 40, balance LKR 10,"
                            + " short by LKR 30");
            assertTrue("no journey was created", capture.lines(Log.FACTORY).isEmpty());
        }
    }

    @Test
    public void aFareThatCannotBePaidAtTheExitMovesTheJourneyToPaymentPendingAndLeavesTheTicketActive() {
        // MLK-PENDING boards with LKR 45 against LKR 40, then exits at S5 where the fare is LKR 69.
        tap("MLK-PENDING", ScanDirection.IN, "S1");
        try (LogCapture capture = new LogCapture(Log.Level.INFO)) {
            tap("MLK-PENDING", ScanDirection.OUT, "S5");

            capture.assertInOrder(
                    "STATE    Journey #1: IN_PROGRESS -> IN_PROGRESS (InProgressState.recordExit",
                    "ACCOUNT  Account #2 (PermanentAccount): isUsableAt -> true, balance LKR 45 against LKR 69"
                            + " -> cannot pay",
                    "JourneyService.attemptSettlement: the account cannot pay, so no money moves",
                    "STATE    Journey #1: IN_PROGRESS -> PAYMENT_PENDING (InProgressState.markPaymentPending",
                    "JourneyService.alight: PAYMENT_PENDING");
            assertTrue("the ticket is used only when the fare is paid",
                    capture.lines(Log.STATE).stream().noneMatch(l -> l.contains("-> USED")));
            assertTrue(capture.lines(Log.ACCOUNT).stream().noneMatch(l -> l.contains("took LKR")));
        }
    }

    @Test
    public void payingALaterFareCompletesTheJourneyAndOnlyThenUsesTheTicket() {                  // L6
        tap("MLK-PENDING", ScanDirection.IN, "S1");
        tap("MLK-PENDING", ScanDirection.OUT, "S5");
        app.testData.credit(2, 100, LATER);
        try (LogCapture capture = new LogCapture(Log.Level.INFO)) {
            app.reader.settle(1, LATER);

            capture.assertInOrder(
                    "JourneyService.settle: retry the unpaid fare of journey #1",
                    "STATE    Journey #1: PAYMENT_PENDING -> COMPLETED (PaymentPendingState.complete: LKR 69 charged)",
                    "STATE    Ticket #4 MLK-PENDING: ACTIVE -> USED (ActiveState.markUsed)");
        }
    }

    @Test
    public void aFlatFareRouteNamesTheFlatStrategyAndNotTheDistanceOne() {                       // L6
        try (LogCapture capture = new LogCapture(Log.Level.INFO)) {
            tap("MLK-FLAT001", ScanDirection.IN, "S1");

            capture.assertInOrder(
                    "FareCalculator: route R200 S1 to S3 is 4.3 km; fare rule #3 in force on 2026-10-21 is FLAT"
                            + " -> FlatFareStrategy",
                    "STRATEGY FlatFareStrategy: flat LKR 30, the 4.3 km are not counted");
            assertTrue(capture.lines(Log.STRATEGY).stream().noneMatch(l -> l.contains("DistanceFareStrategy")));
        }
    }

    @Test
    public void tappingTwiceAtTheExitSaysAlreadySettledAndChargesNothingMore() {                 // L6
        tap("MLK-HAPPY01", ScanDirection.IN, "S1");
        tap("MLK-HAPPY01", ScanDirection.OUT, "S5");
        try (LogCapture capture = new LogCapture(Log.Level.INFO)) {
            tap("MLK-HAPPY01", ScanDirection.OUT, "S5");

            capture.assertInOrder("JourneyService.alight: ALREADY_SETTLED", "ReaderService.display: ALREADY_SETTLED");
            assertTrue(capture.lines(Log.ACCOUNT).isEmpty());
            assertTrue(capture.lines(Log.STATE).isEmpty());
        }
    }

    @Test
    public void aTemporaryAccountNamesItsEndDateWhenItIsCheckedAfterTheVisaEnded() {             // L6
        try (LogCapture capture = new LogCapture(Log.Level.INFO)) {
            tap("MLK-VISAEXP1", ScanDirection.IN, "S1");

            assertTrue(capture.text(), capture.lines(Log.ACCOUNT).stream()
                    .anyMatch(l -> l.contains("TemporaryAccount") && l.contains("isUsableAt -> false")));
        }
    }

    // ---- who is registered, with nothing private in the log (L4)

    @Test
    public void registeringAResidentShowsTheFactoryChoosingAPermanentAccount() {                 // L6
        try (LogCapture capture = new LogCapture(Log.Level.INFO)) {
            app.onboarding.register(resident("Amaya Silva", "amaya@example.com"), MORNING);

            capture.assertInOrder(
                    "OnboardingService.register: a new customer asks to register",
                    "password hashed with PBKDF2 (120000 repeats); only the hash is kept",
                    "UOW      begin:",
                    "FACTORY  Resident.openAccount: passenger #6 is a resident -> a new PermanentAccount (no end date)",
                    "customer #6 registered with a PERMANENT account and LKR 0 credit",
                    "UOW      commit:");
        }
    }

    @Test
    public void registeringANonResidentShowsTheFactoryChoosingATemporaryAccountWithItsEndDate() {   // L6
        try (LogCapture capture = new LogCapture(Log.Level.INFO)) {
            app.onboarding.register(new OnboardingRequest("Tom Lee", "tom@example.com", "a long secret password",
                    ResidencyKind.NON_RESIDENT, LocalDate.parse("2026-12-01")), MORNING);

            capture.assertInOrder(
                    "FACTORY  NonResident.openAccount: passenger #6 is a non-resident -> a new TemporaryAccount"
                            + " (works through 2026-12-01)",
                    "customer #6 registered with a TEMPORARY account");
        }
    }

    @Test
    public void aRefusedRegistrationNamesTheWrongFieldsAndNeverTheirValues() {                   // L4
        try (LogCapture capture = new LogCapture(Log.Level.DEBUG)) {
            try {
                app.onboarding.register(new OnboardingRequest("", "not an address", "short", null, null), MORNING);
                fail("expected the registration to be refused");
            } catch (InvalidRegistrationException expected) {
                // the lines below are what this test is about
            }

            capture.assertInOrder("OnboardingRequest.problems: refused, 4 wrong field(s): name, email, password, residency");
            assertFalse(capture.text(), capture.text().contains("not an address"));
            assertFalse(capture.text(), capture.text().contains("short"));
            assertTrue("nothing was stored, so no unit of work began", capture.lines(Log.UOW).isEmpty());
        }
    }

    @Test
    public void anEmailThatIsAlreadyTakenRollsBackAndSaysWhy() {                                 // L7
        app.onboarding.register(resident("Amaya Silva", "amaya@example.com"), MORNING);
        try (LogCapture capture = new LogCapture(Log.Level.INFO)) {
            try {
                app.onboarding.register(resident("Amaya Two", "amaya@example.com"), MORNING);
                fail("expected EMAIL_TAKEN");
            } catch (DomainException expected) {
                assertEquals("EMAIL_TAKEN", expected.code());
            }

            capture.assertInOrder(
                    "that email is already registered -> EMAIL_TAKEN",
                    "UOW      rollback: DomainException: This email is already registered - every change in this unit"
                            + " of work is undone");
            assertTrue("nothing was created", capture.lines(Log.FACTORY).isEmpty());
        }
    }

    @Test
    public void aFailedLoginIsOneWarningThatDoesNotSayWhichHalfWasWrong() {                      // L4
        app.onboarding.register(resident("Amaya Silva", "amaya@example.com"), MORNING);
        for (String[] attempt : new String[][] {{"amaya@example.com", "the wrong password"},
                {"nobody@example.com", "a long secret password"}}) {
            try (LogCapture capture = new LogCapture(Log.Level.WARN)) {
                try {
                    app.onboarding.login(attempt[0], attempt[1]);
                    fail("expected the login to be refused");
                } catch (DomainException expected) {
                    assertEquals("INVALID_LOGIN", expected.code());
                }

                assertEquals(capture.text(), 1, capture.lines().size());
                assertTrue(capture.text(), capture.text().contains(" WARN "));
                assertTrue(capture.text(), capture.text().contains("OnboardingService.login: refused -> INVALID_LOGIN"));
                assertFalse(capture.text(), capture.text().contains("amaya"));
                assertFalse(capture.text(), capture.text().contains("nobody"));
            }
        }
    }

    @Test
    public void aGoodLoginSaysWhoLoggedInByNumberOnly() {                                        // L4
        app.onboarding.register(resident("Amaya Silva", "amaya@example.com"), MORNING);
        try (LogCapture capture = new LogCapture(Log.Level.INFO)) {
            app.onboarding.login(" Amaya@Example.com ", "a long secret password");

            capture.assertInOrder("UOW      begin:", "UOW      commit:", "OnboardingService.login: customer #6 logged in");
            assertEquals("the look-up is one unit of work and building the view is a quiet read: " + capture.text(), 3,
                    capture.lines().size());
            assertFalse(capture.text(), capture.text().toLowerCase().contains("amaya"));
        }
    }

    @Test
    public void noPasswordNoHashAndNoWholeEmailEverReachesTheConsole() {                         // L4
        try (LogCapture capture = new LogCapture(Log.Level.DEBUG)) {
            app.onboarding.register(resident("Amaya Silva", "amaya@example.com"), MORNING);
            app.onboarding.login("amaya@example.com", "a long secret password");
            try {
                app.onboarding.login("amaya@example.com", "not the password");
            } catch (DomainException expected) {
                // refused
            }
            app.onboarding.login("amaya@example.com", "a long secret password");

            String text = capture.text();
            assertFalse("the password", text.contains("a long secret password"));
            assertFalse("the wrong password", text.contains("not the password"));
            assertFalse("the whole email", text.contains("amaya@example.com"));
            assertFalse("a stored hash looks like iterations:salt:hash", text.matches("(?s).*\\d+:[A-Za-z0-9+/=]{8,}:[A-Za-z0-9+/=]{8,}.*"));
            assertTrue("the credentials row was still traced, with the hash hidden",
                    text.contains("CredentialRepository.insert(6, a password hash (hidden)) -> done"));
            assertTrue("and the email that the repository saw is masked",
                    text.contains("a***@example.com"));
        }
    }

    // ---- booking (L7)

    @Test
    public void bookingShowsTheFactoryIssuingAnActiveTicketAndSaysNoMoneyMoves() {              // L6
        try (LogCapture capture = new LogCapture(Log.Level.INFO)) {
            app.booking.book(new BookingRequest(1, "R100", "S1", "S5", TODAY), MORNING);

            capture.assertInOrder(
                    "UOW      begin:",
                    "BookingService.book: passenger #1, route R100 S1 to S5 on 2026-10-21",
                    "STRATEGY FareCalculator:",
                    "ACCOUNT  Account #1 (PermanentAccount): isUsableAt -> true, balance LKR 500 against LKR 69"
                            + " -> can pay",
                    "FACTORY  QRTicket.issue: new ticket MLK-",
                    "stored as ACTIVE. No money moves; the fare of LKR 69 is charged at the exit",
                    "UOW      commit:");
            assertTrue(capture.text(), capture.lines(Log.FACTORY).get(0).endsWith("fare LKR 69, starts in the ACTIVE state"));
            assertTrue("no debit happens when a ticket is booked", capture.lines(Log.ACCOUNT).stream()
                    .noneMatch(l -> l.contains("took LKR")));
        }
    }

    @Test
    public void aQuoteSaysNothingIsCreated() {                                                   // L7
        try (LogCapture capture = new LogCapture(Log.Level.INFO)) {
            app.booking.quote(new BookingRequest(1, "R100", "S1", "S5", TODAY), MORNING);

            capture.assertInOrder(
                    "BookingService.quote: passenger #1, route R100 S1 to S5 on 2026-10-21 (nothing is created)",
                    "BookingService.quote: fare LKR 69, balance LKR 500, can pay now: true");
            assertTrue(capture.lines(Log.FACTORY).isEmpty());
        }
    }

    @Test
    public void aRefusedBookingNamesTheWrongFieldsAndRollsBack() {                               // L7
        try (LogCapture capture = new LogCapture(Log.Level.INFO)) {
            try {
                app.booking.book(new BookingRequest(1, null, null, null, null), MORNING);
                fail("expected the booking to be refused");
            } catch (InvalidBookingException expected) {
                // the log is the point
            }

            capture.assertInOrder(
                    "BookingRequest.problems: refused, 4 wrong field(s): routeNo, boardingStopId,"
                            + " destinationStopId, travelDate",
                    "UOW      rollback: InvalidBookingException: Invalid request");
            assertTrue(capture.lines(Log.FACTORY).isEmpty());
        }
    }

    @Test
    public void aQrCodeThatIsAlreadyTakenIsSaidSoAndAnotherIsDrawn() {                          // L7
        Fixture f = new Fixture(open(TODAY));
        f.queuedCodes.add("MLK-HAPPY01");                                // already in the demo data
        f.queuedCodes.add("MLK-FRESH001");
        try (LogCapture capture = new LogCapture(Log.Level.INFO)) {
            f.booking.book(new BookingRequest(1, "R100", "S1", "S5", TODAY), MORNING);

            capture.assertInOrder(
                    "BookingService: QR code MLK-HAPPY01 is already taken, drawing another (try 1 of ",
                    "FACTORY  QRTicket.issue: new ticket MLK-FRESH001 for passenger #1");
            assertEquals("only the code that was kept became a ticket", 1, capture.lines(Log.FACTORY).size());
        }
    }

    // ---- the test-data buttons

    @Test
    public void topUpAndStartOverAreShownAsTestDataActions() {
        try (LogCapture capture = new LogCapture(Log.Level.INFO)) {
            app.testData.credit(1, 100, MORNING);
            app.testData.reset();

            capture.assertInOrder(
                    "ACCOUNT  TestDataService.credit: LKR 100 added to account #1, balance LKR 500 -> LKR 600",
                    "SERVICE  TestDataService.reset: every row is removed and the demo data is loaded again",
                    "FACTORY  Resident.openAccount: passenger #1");
        }
    }

    // ---- levels and behaviour (L1, L5, L8, L9)

    @Test
    public void repositoryCallsAreShownOnlyAtDebug() {                                           // L9
        try (LogCapture capture = new LogCapture(Log.Level.INFO)) {
            tap("MLK-HAPPY01", ScanDirection.IN, "S1");

            assertTrue(capture.lines(Log.REPO).isEmpty());
        }
        try (LogCapture capture = new LogCapture(Log.Level.DEBUG)) {
            tap("MLK-HAPPY01", ScanDirection.OUT, "S5");

            assertTrue(capture.text(), capture.lines(Log.REPO).stream()
                    .anyMatch(l -> l.contains("TicketRepository.findByQrCode(\"MLK-HAPPY01\") -> found ticket #1")));
            assertTrue(capture.lines(Log.REPO).stream().anyMatch(l -> l.contains("AccountRepository.save(")));
        }
    }

    @Test
    public void theWarnLevelStaysSilentWhileEverythingGoesWell() {                               // L2
        try (LogCapture capture = new LogCapture(Log.Level.WARN)) {
            tap("MLK-HAPPY01", ScanDirection.IN, "S1");
            tap("MLK-HAPPY01", ScanDirection.OUT, "S5");
            tap("MLK-HAPPY01", ScanDirection.IN, "S1");

            assertEquals(capture.text(), 0, capture.lines().size());
        }
    }

    @Test
    public void withTheLogOffNothingIsPrintedAtAll() {                                           // L1
        try (LogCapture capture = new LogCapture(Log.Level.OFF)) {
            tap("MLK-HAPPY01", ScanDirection.IN, "S1");
            tap("MLK-HAPPY01", ScanDirection.OUT, "S5");
            app.onboarding.register(resident("Amaya Silva", "amaya@example.com"), MORNING);

            assertEquals("", capture.text());
        }
    }

    @Test
    public void everyLineIsOnePlainAsciiLineWithATimeALevelAndATopic() {                         // L3
        try (LogCapture capture = new LogCapture(Log.Level.DEBUG)) {
            tap("MLK-HAPPY01", ScanDirection.IN, "S1");
            tap("MLK-HAPPY01", ScanDirection.OUT, "S5");
            tap("MLK-NOFUNDS", ScanDirection.IN, "S1");
            app.booking.book(new BookingRequest(1, "R100", "S1", "S5", TODAY), MORNING);

            List<String> lines = capture.lines();
            assertTrue(lines.size() > 50);
            for (String line : lines) {
                assertTrue(line, line.matches("\\d\\d:\\d\\d:\\d\\d\\.\\d{3} (WARN |INFO |DEBUG) [-#\\d]{1,6}\\s+[A-Z]+\\s+[\\x20-\\x7e]+"));
            }
        }
    }

    @Test
    public void theProgramDoesTheSameThingWhetherOrNotYouWatch() {                               // L8
        String quiet = journeyAsJson(Log.Level.OFF);
        String watched = journeyAsJson(Log.Level.DEBUG);

        assertEquals(quiet, watched);
    }

    private String journeyAsJson(Log.Level level) {
        Container fresh = Container.create(TODAY);
        try (LogCapture capture = new LogCapture(level)) {
            StringBuilder what = new StringBuilder();
            for (Object[] step : new Object[][] {
                    {"MLK-HAPPY01", ScanDirection.IN, "S1"}, {"MLK-HAPPY01", ScanDirection.OUT, "S5"},
                    {"MLK-HAPPY01", ScanDirection.IN, "S1"}, {"MLK-NOFUNDS", ScanDirection.IN, "S1"},
                    {"MLK-PENDING", ScanDirection.IN, "S1"}, {"MLK-PENDING", ScanDirection.OUT, "S5"}}) {
                ScanDirection direction = (ScanDirection) step[1];
                ReaderDisplay shown = fresh.reader.scan(new ScanRequest((String) step[0], direction, (String) step[2],
                        direction == ScanDirection.IN ? MORNING : LATER));
                what.append(shown.getOutcome()).append('|').append(shown.getTone()).append('|')
                        .append(shown.getMessage()).append('\n');
            }
            what.append(fresh.queries.passengers().size()).append('|').append(fresh.queries.passenger(1).getBalance());
            assertEquals(level == Log.Level.OFF, capture.lines().isEmpty());
            return what.toString();
        }
    }
}
