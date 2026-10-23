package lk.metrolink.journey.domain;

import java.time.Instant;

import java.time.Instant;
import java.time.LocalDate;

public class TemporaryAccount extends Account {

    private final LocalDate visaExpiry;

    public TemporaryAccount(long balance, long passengerId, long accountId, LocalDate visaExpiry) {
        super(balance, passengerId, accountId);
        this.visaExpiry = visaExpiry;
    }

    @Override
    public String type() {
        return "TEMPORARY";
    }

    @Override
    public boolean isUsableAt(Instant at) {
        return !Dates.localDate(at).isAfter(visaExpiry);
    }

    public LocalDate getVisaExpiry() {
        return visaExpiry;
    }

    @Override
    public Account copy() {
        return withId(getAccountId());
    }

    @Override
    public Account withId(long newAccountId) {
        return new TemporaryAccount(getBalance(), getPassengerId(), newAccountId, visaExpiry);
    }
}
