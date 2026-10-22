package lk.metrolink.journey.domain;

import java.time.LocalDate;
import java.util.Optional;

public class CancelledState implements TicketState {

    @Override
    public String name() {
        return "CANCELLED";
    }

    @Override
    public Optional<InvalidTicketReason> refusalReason(LocalDate travelDate, LocalDate today) {
        return Optional.of(InvalidTicketReason.TICKET_CANCELLED);
    }
}
