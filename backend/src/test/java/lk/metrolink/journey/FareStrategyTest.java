package lk.metrolink.journey;

import static org.junit.Assert.assertEquals;

import java.time.LocalDate;
import lk.metrolink.journey.domain.FareRule;
import lk.metrolink.journey.domain.FareType;
import lk.metrolink.journey.fare.DistanceFareStrategy;
import lk.metrolink.journey.fare.FareStrategy;
import lk.metrolink.journey.fare.FlatFareStrategy;
import org.junit.Test;

public class FareStrategyTest {
    private static final LocalDate JULY_1 = LocalDate.parse("2026-07-01");

    private static final FareRule R100_NOW = FareRule.distance(2, "R100", 20, 4.5, JULY_1, null);
    private static final FareRule R100_OLD = FareRule.distance(1, "R100", 20, 4.0,
            LocalDate.parse("2025-01-01"), LocalDate.parse("2026-06-30"));
    private static final FareRule R200 = FareRule.flat(3, "R200", 30, LocalDate.parse("2025-01-01"), null);

    private final FareStrategy distance = new DistanceFareStrategy();
    private final FareStrategy flat = new FlatFareStrategy();

    @Test
    public void eachStrategyKnowsWhichFareTypeItPrices() {
        assertEquals(FareType.DISTANCE, distance.fareType());
        assertEquals(FareType.FLAT, flat.fareType());
    }
    @Test
    public void distanceFaresFollowTheWorkedNumbersInTheRules() {          // X4
        assertEquals(69, distance.calculate(R100_NOW, 10.8));   // 20 + 4.5 x 10.8 = 68.6
        assertEquals(54, distance.calculate(R100_NOW, 7.4));    // 20 + 4.5 x 7.4  = 53.3
        assertEquals(60, distance.calculate(R100_NOW, 8.7));    // 20 + 4.5 x 8.7  = 59.15
    }

    @Test
    public void theOlderRuleGivesTheOlderFare() {                          // X4
        assertEquals(64, distance.calculate(R100_OLD, 10.8));   // 20 + 4.0 x 10.8 = 63.2
    }

    @Test
    public void anExactFareIsNotRoundedUp() {
        assertEquals(30, distance.calculate(R100_OLD, 2.5));    // 20 + 4.0 x 2.5 = 30.0
    }

    @Test
    public void floatingPointNoiseDoesNotAddARupee() {
        FareRule rule = FareRule.distance(9, "RX", 20, 4.4, JULY_1, null);

        // 20 + 4.4 x 22.5 is exactly 119. As a double it is 119.00000000000001.
        assertEquals(119, distance.calculate(rule, 22.5));
    }

    @Test
    public void aFlatFareIgnoresTheDistance() {                            // X4
        assertEquals(30, flat.calculate(R200, 2.1));
        assertEquals(30, flat.calculate(R200, 22.0));
    }

}
