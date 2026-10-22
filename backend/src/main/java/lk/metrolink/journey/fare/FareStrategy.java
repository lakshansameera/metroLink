package lk.metrolink.journey.fare;

import lk.metrolink.journey.domain.FareRule;
import lk.metrolink.journey.domain.FareType;

public interface FareStrategy {
    FareType fareType();
    long calculate(FareRule rule, double distanceKm);
}
