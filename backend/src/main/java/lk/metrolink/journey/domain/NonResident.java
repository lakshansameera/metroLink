package lk.metrolink.journey.domain;

import java.time.LocalDate;
import java.util.Objects;

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
        return new TemporaryAccount(0, passengerId, accountId, visaExpiry);
    }
}
