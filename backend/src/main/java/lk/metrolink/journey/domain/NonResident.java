package lk.metrolink.journey.domain;

import java.time.LocalDate;
import java.util.Objects;
import lk.metrolink.journey.log.Log;

public class NonResident implements Residency{


    private final LocalDate visaExpiry;

    public NonResident(LocalDate visaExpiry) {
        this.visaExpiry = Objects.requireNonNull(visaExpiry, "visaExpiry");
    }

    public LocalDate getVisaExpiry() {
        return visaExpiry;
    }

    @Override
    public String name() {
        return "NON_RESIDENT";
    }

    @Override
    public Account openAccount(long accountId, long passengerId) {
        Log.info(Log.FACTORY, "NonResident.openAccount: passenger #%d is a non-resident -> a new TemporaryAccount"
                + " (works through %s)", passengerId, visaExpiry);
        return new TemporaryAccount(0, passengerId, accountId, visaExpiry);
    }
}
