package lk.metrolink.journey.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

public class PermanentAccount extends Account{

    public PermanentAccount(long balance, long passengerId, long accountId) {
        super(balance, passengerId, accountId);
    }

    @Override
    public String type() {
        return "PERMANENT";
    }

    @Override
    public boolean isUsableAt(Instant at) {
        return true;
    }

    @Override
    public Optional<LocalDate> lastUsableDay() {
        return Optional.empty();
    }

    @Override
    public Account copy() {
        return withId(getAccountId());
    }

    @Override
    public Account withId(long newAccountId) {
        return new PermanentAccount(getBalance(), getPassengerId(), newAccountId);
    }
}
