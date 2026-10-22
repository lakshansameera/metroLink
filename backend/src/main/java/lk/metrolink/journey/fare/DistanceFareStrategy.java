package lk.metrolink.journey.fare;

import lk.metrolink.journey.domain.FareRule;
import lk.metrolink.journey.domain.FareType;

public class DistanceFareStrategy implements FareStrategy {
    @Override
    public FareType fareType() {
        return FareType.DISTANCE;
    }

    private static long roundUpToRupee(double amount) {
        return (long) Math.ceil(Math.round(amount * 100) / 100.0);
    }

    @Override
    public long calculate(FareRule rule, double distanceKm) {
        return roundUpToRupee(rule.getBaseFare() + rule.getRatePerKm() * distanceKm);

    }
}
