package lk.metrolink.journey;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.time.LocalDate;
import lk.metrolink.journey.domain.FareRule;
import lk.metrolink.journey.domain.FareType;
import org.junit.Test;

public class FareRuleTest {

    private static final LocalDate START = LocalDate.parse("2026-07-01");
    private static final LocalDate END = LocalDate.parse("2026-12-31");

    @Test
    public void aDistanceRuleCarriesABaseFareAndARate() {
        FareRule rule = FareRule.distance(2, "R100", 20, 4.5, START, null);

        assertEquals(FareType.DISTANCE, rule.getFareType());
        assertEquals("R100", rule.getRouteNo());
        assertEquals(20, rule.getBaseFare());
        assertEquals(4.5, rule.getRatePerKm(), 0.0);
        assertEquals(0, rule.getFlatAmount());
    }

    @Test
    public void aFlatRuleCarriesOnlyTheFlatAmount() {
        FareRule rule = FareRule.flat(3, "R200", 30, START, null);

        assertEquals(FareType.FLAT, rule.getFareType());
        assertEquals(30, rule.getFlatAmount());
        assertEquals(0, rule.getBaseFare());
        assertEquals(0.0, rule.getRatePerKm(), 0.0);
    }

    @Test
    public void aRuleStartsOnItsFirstDay() {                               // X4
        FareRule rule = FareRule.distance(2, "R100", 20, 4.5, START, null);

        assertFalse(rule.isEffectiveOn(START.minusDays(1)));
        assertTrue(rule.isEffectiveOn(START));
    }

    @Test
    public void aRuleEndsOnItsLastDay() {                                  // X4
        FareRule rule = FareRule.distance(2, "R100", 20, 4.5, START, END);

        assertTrue(rule.isEffectiveOn(END));
        assertFalse(rule.isEffectiveOn(END.plusDays(1)));
    }

    @Test
    public void aRuleWithNoEndDateNeverEnds() {
        FareRule rule = FareRule.flat(3, "R200", 30, START, null);

        assertNull(rule.getEffectiveTo());
        assertTrue(rule.isEffectiveOn(LocalDate.parse("2099-01-01")));
    }

    @Test
    public void exactlyOneOfTwoConsecutiveRulesAppliesOnEveryDay() {       // X4
        FareRule old = FareRule.distance(1, "R100", 20, 4.0, LocalDate.parse("2025-01-01"), LocalDate.parse("2026-06-30"));
        FareRule current = FareRule.distance(2, "R100", 20, 4.5, LocalDate.parse("2026-07-01"), null);

        LocalDate lastOldDay = LocalDate.parse("2026-06-30");
        LocalDate firstNewDay = LocalDate.parse("2026-07-01");

        assertTrue(old.isEffectiveOn(lastOldDay));
        assertFalse(current.isEffectiveOn(lastOldDay));
        assertFalse(old.isEffectiveOn(firstNewDay));
        assertTrue(current.isEffectiveOn(firstNewDay));
    }

    @Test
    public void aRuleCannotEndBeforeItStarts() {
        assertThrows(IllegalArgumentException.class,
                () -> FareRule.flat(3, "R200", 30, START, START.minusDays(1)));
    }

    @Test
    public void aRuleMustHaveAStartDate() {
        assertThrows(NullPointerException.class, () -> FareRule.flat(3, "R200", 30, null, null));
    }
    
}
