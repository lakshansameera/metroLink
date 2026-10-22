package lk.metrolink.journey;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.time.Instant;
import lk.metrolink.journey.domain.DomainException;
import lk.metrolink.journey.domain.Journey;
import org.junit.Test;

public class JourneyTest {

    private static final Instant TAP_IN = Instant.parse("2026-10-21T04:00:00Z");
    private static final Instant TAP_OUT = Instant.parse("2026-10-21T04:40:00Z");

    private static Journey newJourney() {
        return Journey.start(1, "R100", "S1", TAP_IN);
    }

    private static Journey exitedJourney() {
        Journey journey = newJourney();
        journey.recordExit("S5", TAP_OUT, 69);
        return journey;
    }

    @Test
    public void aNewJourneyIsOpenWithNoExitAndNoFare() {                    // B6
        Journey journey = newJourney();

        assertEquals("IN_PROGRESS", journey.status());
        assertTrue(journey.isOpen());
        assertNull(journey.getExitStopId());
        assertNull(journey.getFareDue());
        assertNull(journey.getFareCharged());
    }

    @Test
    public void recordingTheExitRemembersTheFareOwed() {
        Journey journey = exitedJourney();

        assertEquals("S5", journey.getExitStopId());
        assertEquals(TAP_OUT, journey.getTapOutTime());
        assertEquals(Long.valueOf(69), journey.getFareDue());
        assertNull(journey.getFareCharged());
        assertEquals("IN_PROGRESS", journey.status());
    }

    @Test
    public void aPaidJourneyCompletesWithTheFareCharged() {                 // X5
        Journey journey = exitedJourney();

        journey.complete(69);

        assertEquals("COMPLETED", journey.status());
        assertFalse(journey.isOpen());
        assertEquals(Long.valueOf(69), journey.getFareCharged());
    }

    @Test
    public void anUnpaidJourneyWaitsAndKeepsTheFareOwed() {                 // X6
        Journey journey = exitedJourney();

        journey.markPaymentPending();

        assertEquals("PAYMENT_PENDING", journey.status());
        assertEquals(Long.valueOf(69), journey.getFareDue());
        assertNull(journey.getFareCharged());
    }

    @Test
    public void aPendingJourneyCanBeSettledLater() {                        // D1
        Journey journey = exitedJourney();
        journey.markPaymentPending();

        journey.complete(69);

        assertEquals("COMPLETED", journey.status());
        assertEquals(Long.valueOf(69), journey.getFareCharged());
    }

    @Test
    public void aJourneyCannotBeSettledBeforeTheExitIsRecorded() {
        Journey journey = newJourney();

        DomainException e = assertThrows(DomainException.class, () -> journey.complete(69));

        assertEquals("ILLEGAL_TRANSITION", e.code());
        assertEquals("IN_PROGRESS", journey.status());
        assertNull(journey.getFareCharged());
    }

    @Test
    public void aCompletedJourneyCannotBeChangedAgain() {
        Journey journey = exitedJourney();
        journey.complete(69);

        assertThrows(DomainException.class, () -> journey.complete(69));
        assertThrows(DomainException.class, () -> journey.markPaymentPending());
        assertThrows(DomainException.class, () -> journey.recordExit("S4", TAP_OUT, 54));
        assertEquals("S5", journey.getExitStopId());
        assertEquals(Long.valueOf(69), journey.getFareDue());
    }

    @Test
    public void aRefusedMoveChangesNothing() {
        Journey journey = exitedJourney();
        journey.markPaymentPending();

        assertThrows(DomainException.class, () -> journey.recordExit("S4", TAP_OUT, 54));

        assertEquals("S5", journey.getExitStopId());
        assertEquals(Long.valueOf(69), journey.getFareDue());
    }
}