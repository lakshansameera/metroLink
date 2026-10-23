package lk.metrolink.journey.service.view;

import java.util.List;

/** An account with the fares taken from it, newest first. */
public final class AccountStatement {
    private final PassengerView passenger;
    private final List<ChargeView> charges;

    public AccountStatement(PassengerView passenger, List<ChargeView> charges) {
        this.passenger = passenger;
        this.charges = List.copyOf(charges);
    }

    public PassengerView getPassenger() { return passenger; }
    public List<ChargeView> getCharges() { return charges; }
}
