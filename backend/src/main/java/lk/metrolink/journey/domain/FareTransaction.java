package lk.metrolink.journey.domain;

import java.time.Instant;


public final class FareTransaction {
    private final long transactionId;
    private final String reference;
    private final long accountId;
    private final long journeyId;
    private final long amount;
    private final long balanceAfter;
    private final Instant createdAt;

    private FareTransaction(long transactionId, String reference, long accountId, long journeyId, long amount,
                            long balanceAfter, Instant createdAt) {
        this.transactionId = transactionId;
        this.reference = reference;
        this.accountId = accountId;
        this.journeyId = journeyId;
        this.amount = amount;
        this.balanceAfter = balanceAfter;
        this.createdAt = createdAt;
    }


    public static String referenceFor(long journeyId) {
        return "FARE-" + journeyId;
    }

     public static FareTransaction forJourney(long journeyId, long accountId, long amount, long balanceAfter,
                                             Instant createdAt) {
        return new FareTransaction(0, referenceFor(journeyId), accountId, journeyId, amount, balanceAfter, createdAt);
    }

     public FareTransaction withId(long newTransactionId) {
        return new FareTransaction(newTransactionId, reference, accountId, journeyId, amount, balanceAfter, createdAt);
    }

    public long getTransactionId() { return transactionId; }
    public String getReference() { return reference; }
    public long getAccountId() { return accountId; }
    public long getJourneyId() { return journeyId; }
    public long getAmount() { return amount; }
    public long getBalanceAfter() { return balanceAfter; }
    public Instant getCreatedAt() { return createdAt; }
}