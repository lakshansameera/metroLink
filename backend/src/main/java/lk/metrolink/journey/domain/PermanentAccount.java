package lk.metrolink.journey.domain;

import java.time.Instant;

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
    public Account copy() {
        return withId(getAccountId());
    }

    @Override
    public Account withId(long newAccountId) {
        return new PermanentAccount(getBalance(), getPassengerId(), newAccountId);
    }
}
