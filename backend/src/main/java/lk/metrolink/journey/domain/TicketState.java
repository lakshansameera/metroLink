package lk.metrolink.journey.domain;

import java.time.LocalDate;
import java.util.Optional;

public interface TicketState {

    String name();
    Optional<InvalidTicketReason> refusalReason(LocalDate travelDate, LocalDate today);

    default TicketState markUsed(String qrCode) {
        throw new DomainException("Ticket " + qrCode + " is " + name() + ", cannot mark USED",
                "ILLEGAL_TRANSITION", 409);
    }

    static TicketState of(String name) {
        switch (name) {
            case "ACTIVE":    return new ActiveState();
            case "USED":      return new UsedState();
            case "EXPIRED":   return new ExpiredState();
            case "CANCELLED": return new CancelledState();
            default: throw new IllegalArgumentException("Unknown ticket status: " + name);
        }
    }
}
