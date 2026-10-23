package lk.metrolink.journey.fare;

import lk.metrolink.journey.domain.FareType;

public class FareQuote {
    private long amount;
    private double distanceKm;
    private long fareRuleId;
    private FareType fareType;

    public FareQuote(long amount, double distanceKm, long fareRuleId, FareType fareType) {
        this.amount = amount;
        this.distanceKm = distanceKm;
        this.fareRuleId = fareRuleId;
        this.fareType = fareType;
    }

    public long getAmount() { return amount; }
    public double getDistanceKm() { return distanceKm; }
    public long getFareRuleId() { return fareRuleId; }
    public FareType getFareType() { return fareType; }

}
