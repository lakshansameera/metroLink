package lk.metrolink.journey;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import java.time.LocalDate;
import java.util.List;
import lk.metrolink.journey.domain.DomainException;
import lk.metrolink.journey.domain.FareRule;
import lk.metrolink.journey.domain.FareType;
import lk.metrolink.journey.domain.Route;
import lk.metrolink.journey.domain.RouteStop;
import lk.metrolink.journey.fare.FareCalculator;
import lk.metrolink.journey.fare.FareQuote;
import lk.metrolink.journey.fare.FareStrategy;
import lk.metrolink.journey.fare.FlatFareStrategy;
import org.junit.Test;

public class FareCalculatorTest {

    private static final Route R100 = new Route("R100", "Colombo Fort - Ganemulla", List.of(
            new RouteStop("S1", 1, 0), new RouteStop("S2", 2, 2.1), new RouteStop("S3", 3, 4.3),
            new RouteStop("S4", 4, 7.4), new RouteStop("S5", 5, 10.8), new RouteStop("S6", 6, 14.5),
            new RouteStop("S7", 7, 22.0)));

    private static final Route R200 = new Route("R200", "Fort City Circular", List.of(
            new RouteStop("S1", 1, 0), new RouteStop("S2", 2, 2.1), new RouteStop("S3", 3, 4.3)));

    private static final List<FareRule> RULES = List.of(
            FareRule.distance(1, "R100", 20, 4.0, LocalDate.parse("2025-01-01"), LocalDate.parse("2026-06-30")),
            FareRule.distance(2, "R100", 20, 4.5, LocalDate.parse("2026-07-01"), null),
            FareRule.flat(3, "R200", 30, LocalDate.parse("2025-01-01"), null));

    private static final LocalDate TODAY = LocalDate.parse("2026-10-21");

    private final FareCalculator calculator = new FareCalculator();

    @Test
    public void tripsAreChargedTheWorkedFaresInTheRules() {
        assertEquals(69, calculator.calculate(R100, RULES, "S1", "S5", TODAY).getAmount());
        assertEquals(54, calculator.calculate(R100, RULES, "S1", "S4", TODAY).getAmount());
        assertEquals(60, calculator.calculate(R100, RULES, "S2", "S5", TODAY).getAmount());
    }

    @Test
    public void theRuleInForceOnTheTapInDateDecidesThePrice() {            // X4
        assertEquals(64, calculator.calculate(R100, RULES, "S1", "S5", LocalDate.parse("2026-06-30")).getAmount());
        assertEquals(69, calculator.calculate(R100, RULES, "S1", "S5", LocalDate.parse("2026-07-01")).getAmount());
    }

    @Test
    public void aFlatRouteChargesTheFlatAmountForAnyTwoStops() {           // X4
        assertEquals(30, calculator.calculate(R200, RULES, "S1", "S2", TODAY).getAmount());
        assertEquals(30, calculator.calculate(R200, RULES, "S1", "S3", TODAY).getAmount());
    }

    @Test
    public void thePriceFollowsTheStopWherePassengerGotOffNotThePlannedStop() {   // X3
        long planned = calculator.calculate(R100, RULES, "S1", "S4", TODAY).getAmount();
        long actual = calculator.calculate(R100, RULES, "S1", "S5", TODAY).getAmount();

        assertEquals(54, planned);
        assertEquals(69, actual);
    }

    @Test
    public void theQuoteShowsHowThePriceWasReached() {
        FareQuote quote = calculator.calculate(R100, RULES, "S1", "S5", TODAY);

        assertEquals(10.8, quote.getDistanceKm(), 0.0001);
        assertEquals(2, quote.getFareRuleId());
        assertEquals(FareType.DISTANCE, quote.getFareType());
    }

    @Test
    public void rulesOfOtherRoutesAreIgnored() {
        List<FareRule> onlyR200 = List.of(FareRule.flat(3, "R200", 30, LocalDate.parse("2025-01-01"), null));

        DomainException e = assertThrows(DomainException.class,
                () -> calculator.calculate(R100, onlyR200, "S1", "S5", TODAY));

        assertEquals("NO_FARE_RULE", e.code());
    }

    @Test
    public void whenTwoRulesOverlapTheOneThatStartedLaterWins() {
        List<FareRule> overlapping = List.of(
                FareRule.flat(1, "R200", 30, LocalDate.parse("2025-01-01"), null),
                FareRule.flat(2, "R200", 35, LocalDate.parse("2026-01-01"), null));

        assertEquals(35, calculator.calculate(R200, overlapping, "S1", "S2", TODAY).getAmount());
    }

    @Test
    public void aDateWithNoRuleInForceIsAnError() {
        DomainException e = assertThrows(DomainException.class,
                () -> calculator.calculate(R100, RULES, "S1", "S5", LocalDate.parse("2024-12-31")));

        assertEquals("NO_FARE_RULE", e.code());
        assertEquals(500, e.status());
    }

    @Test
    public void routeErrorsPassThroughBeforeAnyPriceIsWorkedOut() {         // X2
        DomainException notOnRoute = assertThrows(DomainException.class,
                () -> calculator.calculate(R100, RULES, "S1", "S9", TODAY));
        DomainException backwards = assertThrows(DomainException.class,
                () -> calculator.calculate(R100, RULES, "S5", "S2", TODAY));

        assertEquals("STOP_NOT_ON_ROUTE", notOnRoute.code());
        assertEquals("EXIT_NOT_AFTER_BOARDING", backwards.code());
    }

    @Test
    public void aRuleWithNoRegisteredStrategyIsAnError() {
        FareCalculator flatOnly = new FareCalculator(List.of(new FlatFareStrategy()));

        DomainException e = assertThrows(DomainException.class,
                () -> flatOnly.calculate(R100, RULES, "S1", "S5", TODAY));

        assertEquals("NO_STRATEGY", e.code());
    }

    @Test
    public void aNewFareSystemCanBePluggedInWithoutChangingTheCalculator() {
        FareStrategy everythingCostsTen = new FareStrategy() {
            @Override
            public FareType fareType() {
                return FareType.DISTANCE;
            }

            @Override
            public long calculate(FareRule rule, double distanceKm) {
                return 10;
            }
        };
        FareCalculator custom = new FareCalculator(List.of(everythingCostsTen));

        assertEquals(10, custom.calculate(R100, RULES, "S1", "S5", TODAY).getAmount());
    }
}