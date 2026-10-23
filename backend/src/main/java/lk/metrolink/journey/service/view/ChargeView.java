package lk.metrolink.journey.service.view;

import java.time.Instant;

/** One fare taken from an account. */
public final class ChargeView {
    private final long transactionId;
    private final String reference;
    private final long journeyId;
    private final long amount;
    private final long balanceAfter;
    private final Instant createdAt;

    public ChargeView(long transactionId, String reference, long journeyId, long amount, long balanceAfter,
                      Instant createdAt) {
        this.transactionId = transactionId;
        this.reference = reference;
        this.journeyId = journeyId;
        this.amount = amount;
        this.balanceAfter = balanceAfter;
        this.createdAt = createdAt;
    }

    public long getTransactionId() { return transactionId; }
    public String getReference() { return reference; }
    public long getJourneyId() { return journeyId; }
    public long getAmount() { return amount; }
    public long getBalanceAfter() { return balanceAfter; }
    public Instant getCreatedAt() { return createdAt; }
}
