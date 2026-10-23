package lk.metrolink.journey.fare;

import lk.metrolink.journey.domain.FareRule;
import lk.metrolink.journey.domain.FareType;
import lk.metrolink.journey.log.Log;

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
        double exact = rule.getBaseFare() + rule.getRatePerKm() * distanceKm;
        long fare = roundUpToRupee(exact);
        Log.info(Log.STRATEGY, "DistanceFareStrategy: base LKR %d + LKR %s per km x %s km = LKR %.2f, rounded up to LKR %d",
                rule.getBaseFare(), rule.getRatePerKm(), distanceKm, exact, fare);
        return fare;
    }
}
