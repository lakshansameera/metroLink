package lk.metrolink.journey.domain;
import java.time.Instant;

public abstract class Account {

    private long accountId;
    private long passengerId;
    private long balance;

    public Account(long balance, long passengerId, long accountId) {
        this.balance = balance;
        this.passengerId = passengerId;
        this.accountId = accountId;
    }

    public long getAccountId() {
        return accountId;
    }

    public long getPassengerId() {
        return passengerId;
    }

    public long getBalance() {
        return balance;
    }

    public abstract Account copy();

    /** The same account (same kind, same balance) with the id the store gave it. A new account starts with id 0. */
    public abstract Account withId(long newAccountId);

    public abstract String type();

    public abstract boolean isUsableAt(Instant at);

    public final boolean hasSufficientCredit(long amount) {
        return balance >= amount;
    }

    public final boolean canPay(long amount, Instant at) {
        return isUsableAt(at) && hasSufficientCredit(amount);
    }

    private void requireUsable(Instant at) {
        if (!isUsableAt(at)) {
            throw new DomainException("Account " + accountId + " has expired", "ACCOUNT_EXPIRED", 422);
        }
    }

    public final long deduct(long amount, Instant at) {
        requireUsable(at);
        if (amount < 0) throw new DomainException("Deduction must be a non-negative whole amount", "BAD_AMOUNT", 400);
        if (!hasSufficientCredit(amount)) throw new InsufficientCreditException(amount, balance);
        balance -= amount;
        return balance;
    }

    public final long credit(long amount, Instant at) {
        requireUsable(at);
        if (amount <= 0) throw new DomainException("Credit must be a positive whole amount", "BAD_AMOUNT", 400);
        balance += amount;
        return balance;
    }
}
