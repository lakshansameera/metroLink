package lk.metrolink.journey;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.time.Instant;
import java.time.LocalDate;
import lk.metrolink.journey.domain.DomainException;
import lk.metrolink.journey.domain.Journey;
import lk.metrolink.journey.domain.QRTicket;
import lk.metrolink.journey.domain.TicketState;
import lk.metrolink.journey.log.Log;
import lk.metrolink.journey.log.LogCapture;
import org.junit.After;
import org.junit.Test;

/**
 * The State pattern as the console shows it (rule L6): every change names the old state, the new state and the State
 * class that allowed it, and a change that is not allowed is a warning and changes nothing.
 */
public class StateLogTest {

    private static final LocalDate TODAY = LocalDate.parse("2026-10-21");
    private static final Instant TAP_IN = Instant.parse("2026-10-21T04:00:00Z");
    private static final Instant TAP_OUT = Instant.parse("2026-10-21T04:40:00Z");

    @After
    public void quiet() {
        Log.reset();
    }

    private static QRTicket ticket(String status) {
        return new QRTicket(7, "MLK-TEST", 1, "R100", "S1", "S5", TODAY, 69, Instant.parse("2026-10-20T04:00:00Z"),
                TicketState.of(status));
    }

    private static Journey journey() {
        return new Journey(3, 7, "R100", "S1", TAP_IN, null, null, null, null,
                lk.metrolink.journey.domain.JourneyState.of("IN_PROGRESS"));
    }

    @Test
    public void aJourneyThatRunsItsCourseNamesEveryStateAndEveryStateClass() {
        Journey journey = journey();
        try (LogCapture capture = new LogCapture(Log.Level.INFO)) {
            journey.recordExit("S5", TAP_OUT, 69);
            journey.markPaymentPending();
            journey.complete(69);

            assertEquals(capture.text(), 3, capture.lines(Log.STATE).size());
            capture.assertInOrder(
                    "Journey #3: IN_PROGRESS -> IN_PROGRESS (InProgressState.recordExit: left at S5, fare due LKR 69)",
                    "Journey #3: IN_PROGRESS -> PAYMENT_PENDING (InProgressState.markPaymentPending: the fare of LKR 69"
                            + " is unpaid)",
                    "Journey #3: PAYMENT_PENDING -> COMPLETED (PaymentPendingState.complete: LKR 69 charged)");
        }
        assertEquals("COMPLETED", journey.status());
    }

    @Test
    public void aTicketThatIsUsedNamesTheStateThatAllowedIt() {
        QRTicket ticket = ticket("ACTIVE");
        try (LogCapture capture = new LogCapture(Log.Level.INFO)) {
            ticket.markUsed();

            assertEquals(capture.text(), 1, capture.lines().size());
            capture.assertInOrder("STATE    Ticket #7 MLK-TEST: ACTIVE -> USED (ActiveState.markUsed)");
        }
        assertEquals("USED", ticket.status());
    }

    @Test
    public void usingATicketTwiceIsAWarningAndTheTicketStaysUsed() {
        QRTicket ticket = ticket("USED");
        try (LogCapture capture = new LogCapture(Log.Level.WARN)) {
            try {
                ticket.markUsed();
                fail("a used ticket cannot be used again");
            } catch (DomainException expected) {
                assertEquals("ILLEGAL_TRANSITION", expected.code());
            }

            assertEquals(capture.text(), 1, capture.lines().size());
            assertTrue(capture.text(), capture.text().contains(" WARN "));
            assertTrue(capture.text(), capture.text().contains("STATE    Ticket MLK-TEST is USED: UsedState has no way to markUsed"));
            assertTrue(capture.text(), capture.text().contains("refused"));
        }
        assertEquals("USED", ticket.status());
    }

    @Test
    public void everyKindOfTicketThatCannotBeUsedWarnsWithItsOwnStateClass() {
        for (String status : new String[] {"USED", "EXPIRED", "CANCELLED"}) {
            try (LogCapture capture = new LogCapture(Log.Level.WARN)) {
                try {
                    ticket(status).markUsed();
                    fail(status + " must refuse");
                } catch (DomainException expected) {
                    // the warning is what this test reads
                }

                String expectedClass = status.charAt(0) + status.substring(1).toLowerCase() + "State";
                assertEquals(capture.text(), 1, capture.lines().size());
                assertTrue(capture.text(), capture.text().contains(expectedClass));
            }
        }
    }

    @Test
    public void aJourneyThatIsAlreadyCompletedRefusesEveryMoveWithAWarningAndStaysCompleted() {
        Journey journey = journey();
        journey.recordExit("S5", TAP_OUT, 69);
        journey.complete(69);
        try (LogCapture capture = new LogCapture(Log.Level.WARN)) {
            for (Runnable move : new Runnable[] {
                    () -> journey.recordExit("S5", TAP_OUT, 69),
                    journey::markPaymentPending,
                    () -> journey.complete(69)}) {
                try {
                    move.run();
                    fail("a completed journey allows no move");
                } catch (DomainException expected) {
                    assertEquals("ILLEGAL_TRANSITION", expected.code());
                }
            }

            assertEquals(capture.text(), 3, capture.lines().size());
            assertTrue(capture.lines().stream().allMatch(l -> l.contains(" WARN ")
                    && l.contains("Journey #3 is COMPLETED: CompletedState has no way to ")));
            capture.assertInOrder("record an exit", "mark payment pending", "complete");
        }
        assertEquals("COMPLETED", journey.status());
    }

    @Test
    public void aPaymentPendingJourneyRefusesASecondExitButAcceptsPayment() {
        Journey journey = journey();
        journey.recordExit("S5", TAP_OUT, 69);
        journey.markPaymentPending();
        try (LogCapture capture = new LogCapture(Log.Level.INFO)) {
            try {
                journey.recordExit("S4", TAP_OUT, 54);
                fail("the exit is already recorded");
            } catch (DomainException expected) {
                assertEquals("ILLEGAL_TRANSITION", expected.code());
            }
            journey.complete(69);

            capture.assertInOrder("WARN  -     STATE    Journey #3 is PAYMENT_PENDING: PaymentPendingState has no way to"
                            + " record an exit",
                    "Journey #3: PAYMENT_PENDING -> COMPLETED (PaymentPendingState.complete");
        }
    }

    @Test
    public void nothingIsPrintedWhileTheLogIsOffAndTheStatesStillChange() {
        Journey journey = journey();
        assertEquals(Log.Level.OFF, Log.level());

        journey.recordExit("S5", TAP_OUT, 69);
        journey.complete(69);

        assertEquals("COMPLETED", journey.status());
    }
}
