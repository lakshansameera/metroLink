package lk.metrolink.journey;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

import lk.metrolink.journey.domain.CompletedState;
import lk.metrolink.journey.domain.DomainException;
import lk.metrolink.journey.domain.InProgressState;
import lk.metrolink.journey.domain.JourneyState;
import lk.metrolink.journey.domain.PaymentPendingState;

public class JourneyStateTest {

    @Test
    public void anInProgressJourneyCanRecordExitPayOrWaitForPayment() {
        JourneyState inProgress = new InProgressState();

        assertEquals("IN_PROGRESS", inProgress.recordExit(1).name());
        assertEquals("PAYMENT_PENDING", inProgress.markPaymentPending(1).name());
        assertEquals("COMPLETED", inProgress.complete(1).name());
    }

    @Test
    public void aPaymentPendingJourneyCanOnlyBeCompleted() { 
        JourneyState pending = new PaymentPendingState();

        assertEquals("COMPLETED", pending.complete(1).name());
        assertIllegal(() -> pending.recordExit(1));
        assertIllegal(() -> pending.markPaymentPending(1));
    }

    @Test
    public void aCompletedJourneyAllowsNoMove() {
        JourneyState completed = new CompletedState();

        assertIllegal(() -> completed.recordExit(1));
        assertIllegal(() -> completed.markPaymentPending(1));
        assertIllegal(() -> completed.complete(1));
    }

    @Test
    public void onlyAnInProgressJourneyIsOpen() {
        assertTrue(new InProgressState().isOpen());
        assertFalse(new PaymentPendingState().isOpen());
        assertFalse(new CompletedState().isOpen());
    }

    @Test
    public void theErrorNamesTheJourneyTheStatusAndTheMove() {
        DomainException e = assertThrows(DomainException.class, () -> new CompletedState().complete(7));

        assertEquals("Journey 7 is COMPLETED; cannot complete", e.getMessage());
    }

    @Test
    public void everyStateCanBeRebuiltFromItsName() {
        for (String name : new String[] { "IN_PROGRESS", "PAYMENT_PENDING", "COMPLETED" }) {
            assertEquals(name, JourneyState.of(name).name());
        }
        assertThrows(IllegalArgumentException.class, () -> JourneyState.of("LOST"));
    }

    private static void assertIllegal(Runnable move) {
        DomainException e = assertThrows(DomainException.class, move::run);

        assertEquals("ILLEGAL_TRANSITION", e.code());
        assertEquals(409, e.status());
    }
}