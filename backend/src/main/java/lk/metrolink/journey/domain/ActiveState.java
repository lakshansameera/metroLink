package lk.metrolink.journey.domain;

import java.time.LocalDate;
import java.util.Optional;

public class ActiveState implements TicketState {

    @Override
    public String name() {
        return "ACTIVE";
    }

    @Override
    public Optional<InvalidTicketReason> refusalReason(LocalDate travelDate, LocalDate today) {
        if (travelDate.isBefore(today)) return Optional.of(InvalidTicketReason.TICKET_EXPIRED);
        if (travelDate.isAfter(today)) return Optional.of(InvalidTicketReason.WRONG_TRAVEL_DATE);
        return Optional.empty();
    }

    @Override
    public TicketState markUsed(String qrCode) {
        return new UsedState();
    }
}
