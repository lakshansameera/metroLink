package lk.metrolink.journey.domain;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import lk.metrolink.journey.log.Log;

public interface TicketState {

    String name();
    Optional<InvalidTicketReason> refusalReason(LocalDate travelDate, LocalDate today);

    default TicketState markUsed(String qrCode) {
        Log.warn(Log.STATE, "Ticket %s is %s: %s has no way to markUsed, so the change is refused",
                qrCode, name(), getClass().getSimpleName());
        throw new DomainException("Ticket " + qrCode + " is " + name() + ", cannot mark USED",
                "ILLEGAL_TRANSITION", 409);
    }

    /** Every state a ticket can be in, spelled as it is stored and sent to the screen. */
    static List<String> names() {
        return List.of("ACTIVE", "USED", "EXPIRED", "CANCELLED");
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
