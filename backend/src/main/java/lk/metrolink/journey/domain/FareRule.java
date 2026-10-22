package lk.metrolink.journey.domain;

import java.time.LocalDate;
import java.util.Objects;

public class FareRule {

    private long fareRuleId;
    private String routeNo;
    private FareType fareType;
    private long baseFare;
    private double ratePerKm;
    private long flatAmount;
    private LocalDate effectiveFrom;
    private LocalDate effectiveTo;

    private FareRule(long fareRuleId, String routeNo, FareType fareType, long baseFare, double ratePerKm,
                     long flatAmount, LocalDate effectiveFrom, LocalDate effectiveTo) {
        Objects.requireNonNull(effectiveFrom, "effectiveFrom");
        if (effectiveTo != null && effectiveTo.isBefore(effectiveFrom)) {
            throw new IllegalArgumentException("A fare rule cannot end before it starts");
        }
        this.fareRuleId = fareRuleId;
        this.routeNo = routeNo;
        this.fareType = fareType;
        this.baseFare = baseFare;
        this.ratePerKm = ratePerKm;
        this.flatAmount = flatAmount;
        this.effectiveFrom = effectiveFrom;
        this.effectiveTo = effectiveTo;
    }

    /** Base fare plus a rate for every km. Pass null as effectiveTo for a rule with no end date. */
    public static FareRule distance(long fareRuleId, String routeNo, long baseFare, double ratePerKm,
                                    LocalDate effectiveFrom, LocalDate effectiveTo) {
        return new FareRule(fareRuleId, routeNo, FareType.DISTANCE, baseFare, ratePerKm, 0,
                effectiveFrom, effectiveTo);
    }

    /** One price whatever the distance. Pass null as effectiveTo for a rule with no end date. */
    public static FareRule flat(long fareRuleId, String routeNo, long flatAmount,
                                LocalDate effectiveFrom, LocalDate effectiveTo) {
        return new FareRule(fareRuleId, routeNo, FareType.FLAT, 0, 0, flatAmount,
                effectiveFrom, effectiveTo);
    }

    public long getFareRuleId() { return fareRuleId; }
    public String getRouteNo() { return routeNo; }
    public FareType getFareType() { return fareType; }
    public long getBaseFare() { return baseFare; }
    public double getRatePerKm() { return ratePerKm; }
    public long getFlatAmount() { return flatAmount; }
    public LocalDate getEffectiveFrom() { return effectiveFrom; }
    public LocalDate getEffectiveTo() { return effectiveTo; }

    /** In force from its first day to its last day, both included. */
    public boolean isEffectiveOn(LocalDate date) {
        return !effectiveFrom.isAfter(date) && (effectiveTo == null || !effectiveTo.isBefore(date));
    }
}
