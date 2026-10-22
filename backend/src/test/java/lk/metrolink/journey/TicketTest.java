package lk.metrolink.journey;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import lk.metrolink.journey.domain.DomainException;
import lk.metrolink.journey.domain.InvalidTicketReason;
import lk.metrolink.journey.domain.QRTicket;
import lk.metrolink.journey.domain.TicketState;
import org.junit.Test;

public class TicketTest {

    private static final LocalDate TODAY = LocalDate.parse("2026-10-21");

    private static QRTicket ticket(String status, LocalDate travelDate) {
        return new QRTicket(1, "MLK-TEST", 1, "R100", "S1", "S5", travelDate, 69,
                Instant.parse("2026-10-20T04:00:00Z"), TicketState.of(status));
    }

    @Test
    public void anActiveTicketForTodayMayBeUsed() {
        QRTicket ticket = ticket("ACTIVE", TODAY);

        assertTrue(ticket.refusalReason(TODAY).isEmpty());
    }

    @Test
    public void theTicketAsksItsStateWhyItIsRefused() {                     // B2
        assertEquals(Optional.of(InvalidTicketReason.TICKET_EXPIRED),
                ticket("ACTIVE", LocalDate.parse("2026-10-20")).refusalReason(TODAY));
        assertEquals(Optional.of(InvalidTicketReason.WRONG_TRAVEL_DATE),
                ticket("ACTIVE", LocalDate.parse("2026-10-22")).refusalReason(TODAY));
        assertEquals(Optional.of(InvalidTicketReason.TICKET_ALREADY_USED),
                ticket("USED", TODAY).refusalReason(TODAY));
        assertEquals(Optional.of(InvalidTicketReason.TICKET_CANCELLED),
                ticket("CANCELLED", TODAY).refusalReason(TODAY));
    }

    @Test
    public void markingUsedChangesTheStatusAndTheTicketIsThenRefused() {
        QRTicket ticket = ticket("ACTIVE", TODAY);

        ticket.markUsed();

        assertEquals("USED", ticket.status());
        assertEquals(Optional.of(InvalidTicketReason.TICKET_ALREADY_USED), ticket.refusalReason(TODAY));
    }

    @Test
    public void aTicketCannotBeUsedTwice() {
        QRTicket ticket = ticket("ACTIVE", TODAY);
        ticket.markUsed();

        DomainException e = assertThrows(DomainException.class, ticket::markUsed);

        assertEquals("ILLEGAL_TRANSITION", e.code());
        assertEquals(409, e.status());
    }

    @Test
    public void aCancelledTicketCannotBeMarkedUsed() {
        QRTicket ticket = ticket("CANCELLED", TODAY);

        assertThrows(DomainException.class, ticket::markUsed);
        assertEquals("CANCELLED", ticket.status());
    }
}