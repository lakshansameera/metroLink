package lk.metrolink.journey.service;

import lk.metrolink.journey.domain.InvalidTicketReason;
import lk.metrolink.journey.domain.QRTicket;

/** The answer to "may this ticket start a journey?". Build one with {@link #ok} or {@link #refused}. */
public final class TicketCheck {
    private final QRTicket ticket;
    private final InvalidTicketReason reason;

    private TicketCheck(QRTicket ticket, InvalidTicketReason reason) {
        this.ticket = ticket;
        this.reason = reason;
    }

    public static TicketCheck ok(QRTicket ticket) {
        return new TicketCheck(ticket, null);
    }

    /** The ticket is null when the token matched nothing. */
    public static TicketCheck refused(InvalidTicketReason reason, QRTicket ticket) {
        return new TicketCheck(ticket, reason);
    }

    public boolean isValid() { return reason == null; }
    public InvalidTicketReason getReason() { return reason; }
    public QRTicket getTicket() { return ticket; }
}
