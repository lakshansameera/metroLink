package lk.metrolink.journey.service;

import java.time.LocalDate;
import java.util.Optional;
import lk.metrolink.journey.domain.InvalidTicketReason;
import lk.metrolink.journey.domain.Journey;
import lk.metrolink.journey.domain.QRTicket;
import lk.metrolink.journey.repository.JourneyRepository;
import lk.metrolink.journey.repository.TicketRepository;

/**
 * Decides whether a QR token may start a journey (rules B1 to B3).
 *
 * <p>The order matters: the token must exist (B1), the ticket's own state and date must allow it (B2), and it must
 * not already have a journey (B3). The ticket decides about itself; this class only adds the facts the ticket
 * cannot know, which are "does it exist" and "does it already have a journey".
 */
public final class QRTicketService {
    private final TicketRepository tickets;
    private final JourneyRepository journeys;

    public QRTicketService(TicketRepository tickets, JourneyRepository journeys) {
        this.tickets = tickets;
        this.journeys = journeys;
    }

    /** Must run inside a unit of work. {@code today} is the Colombo date of the scan. */
    public TicketCheck validate(String qrCode, LocalDate today) {
        Optional<QRTicket> found = tickets.findByQrCode(qrCode);
        if (found.isEmpty()) {
            return TicketCheck.refused(InvalidTicketReason.UNKNOWN_TOKEN, null);
        }
        QRTicket ticket = found.get();

        Optional<InvalidTicketReason> own = ticket.refusalReason(today);
        if (own.isPresent()) {
            return TicketCheck.refused(own.get(), ticket);
        }

        Optional<Journey> existing = journeys.findByTicketId(ticket.getTicketId());
        if (existing.isPresent()) {
            // A journey still open means the passenger is already on board. Any other journey means this ticket
            // has been through the system already.
            InvalidTicketReason reason = existing.get().isOpen()
                    ? InvalidTicketReason.ALREADY_BOARDED
                    : InvalidTicketReason.TICKET_ALREADY_USED;
            return TicketCheck.refused(reason, ticket);
        }
        return TicketCheck.ok(ticket);
    }
}
