package lk.metrolink.journey.fare;

import lk.metrolink.journey.domain.FareRule;
import lk.metrolink.journey.domain.FareType;

public class FlatFareStrategy implements FareStrategy{
    @Override
    public FareType fareType() {
        return FareType.FLAT;
    }

    @Override
    public long calculate(FareRule rule, double distanceKm) {
        return rule.getFlatAmount();
    }
}
