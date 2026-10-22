package lk.metrolink.journey.domain;

import java.time.LocalDate;
import java.util.Optional;

public class ExpiredState implements TicketState{

    @Override
    public String name() {
        return "EXPIRED";
    }

    @Override
    public Optional<InvalidTicketReason> refusalReason(LocalDate travelDate, LocalDate today) {
        return Optional.of(InvalidTicketReason.TICKET_EXPIRED);
    }
}
