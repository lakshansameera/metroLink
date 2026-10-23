package lk.metrolink.journey.fare;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import lk.metrolink.journey.domain.DomainException;
import lk.metrolink.journey.domain.FareRule;
import lk.metrolink.journey.domain.FareType;
import lk.metrolink.journey.domain.Route;
import lk.metrolink.journey.log.Log;

public class FareCalculator {

    private final Map<FareType, FareStrategy> strategies = new EnumMap<>(FareType.class);

    /** The normal calculator, with the two fare systems we have today. */
    public FareCalculator() {
        this(List.of(new DistanceFareStrategy(), new FlatFareStrategy()));
    }

    /** A calculator with exactly these strategies.
     * Each one is filed under the type it prices. */
    public FareCalculator(List<FareStrategy> available) {
        for (FareStrategy strategy : available) {
            strategies.put(strategy.fareType(), strategy);
        }
    }

    /**
     * @param rules  the fare rules that may apply (rules of other routes are ignored)
     * @param onDate the tap-in date in Asia/Colombo time (rule X4)
     */
    public FareQuote calculate(Route route, List<FareRule> rules, String boardingStopId, String exitStopId,
                               LocalDate onDate) {
        double distanceKm = route.distanceKm(boardingStopId, exitStopId);   // X2 and X3
        FareRule rule = ruleInForce(route.getRouteNo(), rules, onDate);     // X4
        FareStrategy strategy = strategies.get(rule.getFareType());
        if (strategy == null) {
            throw new DomainException("No fare strategy registered for " + rule.getFareType(),
                    "NO_STRATEGY", 500);
        }
        Log.info(Log.STRATEGY, "FareCalculator: route %s %s to %s is %s km; fare rule #%d in force on %s is %s -> %s",
                route.getRouteNo(), boardingStopId, exitStopId, distanceKm, rule.getFareRuleId(), onDate,
                rule.getFareType(), strategy.getClass().getSimpleName());
        long amount = strategy.calculate(rule, distanceKm);
        return new FareQuote(amount, distanceKm, rule.getFareRuleId(), rule.getFareType());
    }

    /** If two rules overlap on a date, the one that started later wins. */
    private static FareRule ruleInForce(String routeNo, List<FareRule> rules, LocalDate onDate) {
        return rules.stream()
                .filter(r -> r.getRouteNo().equals(routeNo) && r.isEffectiveOn(onDate))
                .max(Comparator.comparing(FareRule::getEffectiveFrom))
                .orElseThrow(() -> new DomainException(
                        "No fare rule in force for route " + routeNo + " on " + onDate, "NO_FARE_RULE", 500));
    }
}
