package lk.metrolink.journey.fare;

import lk.metrolink.journey.domain.FareRule;
import lk.metrolink.journey.domain.FareType;
import lk.metrolink.journey.log.Log;

public class FlatFareStrategy implements FareStrategy{
    @Override
    public FareType fareType() {
        return FareType.FLAT;
    }

    @Override
    public long calculate(FareRule rule, double distanceKm) {
        Log.info(Log.STRATEGY, "FlatFareStrategy: flat LKR %d, the %s km are not counted", rule.getFlatAmount(),
                distanceKm);
        return rule.getFlatAmount();
    }
}
