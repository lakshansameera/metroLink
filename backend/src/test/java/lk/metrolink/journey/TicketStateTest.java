package lk.metrolink.journey;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.time.LocalDate;
import java.util.Optional;
import lk.metrolink.journey.domain.ActiveState;
import lk.metrolink.journey.domain.CancelledState;
import lk.metrolink.journey.domain.DomainException;
import lk.metrolink.journey.domain.ExpiredState;
import lk.metrolink.journey.domain.InvalidTicketReason;
import lk.metrolink.journey.domain.TicketState;
import lk.metrolink.journey.domain.UsedState;
import org.junit.Test;

public class TicketStateTest {
    private static final LocalDate TODAY = LocalDate.parse("2026-10-21");

    @Test
    public void anActiveTicketIsAcceptedOnlyOnItsTravelDate() {            // B2
        TicketState active = new ActiveState();

        assertTrue(active.refusalReason(TODAY, TODAY).isEmpty());
        assertEquals(Optional.of(InvalidTicketReason.TICKET_EXPIRED),
                active.refusalReason(LocalDate.parse("2026-10-20"), TODAY));
        assertEquals(Optional.of(InvalidTicketReason.WRONG_TRAVEL_DATE),
                active.refusalReason(LocalDate.parse("2026-10-22"), TODAY));
    }

    @Test
    public void usedExpiredAndCancelledTicketsAreAlwaysRefused() {         // B2
        // Even with today's travel date, the status alone decides.
        assertEquals(Optional.of(InvalidTicketReason.TICKET_ALREADY_USED),
                new UsedState().refusalReason(TODAY, TODAY));
        assertEquals(Optional.of(InvalidTicketReason.TICKET_EXPIRED),
                new ExpiredState().refusalReason(TODAY, TODAY));
        assertEquals(Optional.of(InvalidTicketReason.TICKET_CANCELLED),
                new CancelledState().refusalReason(TODAY, TODAY));
    }

    @Test
    public void anActiveTicketMovesToUsed() {
        TicketState next = new ActiveState().markUsed("MLK-X");

        assertEquals("USED", next.name());
    }

    @Test
    public void onlyAnActiveTicketCanBeMarkedUsed() {
        for (TicketState state : new TicketState[] { new UsedState(), new ExpiredState(), new CancelledState() }) {
            DomainException e = assertThrows(DomainException.class, () -> state.markUsed("MLK-X"));

            assertEquals("ILLEGAL_TRANSITION", e.code());
            assertEquals(409, e.status());
        }
    }

    @Test
    public void everyStateCanBeRebuiltFromItsName() {
        for (String name : new String[] { "ACTIVE", "USED", "EXPIRED", "CANCELLED" }) {
            assertEquals(name, TicketState.of(name).name());
        }
        assertThrows(IllegalArgumentException.class, () -> TicketState.of("LOST"));
    }
}
