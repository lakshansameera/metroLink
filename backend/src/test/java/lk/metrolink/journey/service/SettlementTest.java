package lk.metrolink.journey.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

import java.time.Instant;
import lk.metrolink.journey.domain.DomainException;
import lk.metrolink.journey.domain.Journey;
import lk.metrolink.journey.domain.NotFoundException;
import org.junit.Before;
import org.junit.Test;

/** Rule D1: a PAYMENT_PENDING journey can be settled later. */
public class SettlementTest {

    private static final Instant AFTER_TOP_UP = Instant.parse("2026-10-21T08:00:00Z");

    private Fixture f;
    private long pendingJourneyId;

    @Before
    public void setUp() {
        f = new Fixture();
        f.in("MLK-PENDING", "S1", Fixture.MORNING);
        f.out("MLK-PENDING", "S5", Fixture.LATER);                                               // owes 69, has 45
        pendingJourneyId = f.journeyOf("MLK-PENDING").getJourneyId();
    }

    @Test
    public void settlingBeforeTheTopUpChangesNothing() {
        ScanResult result = f.journeyService.settle(pendingJourneyId, AFTER_TOP_UP);

        assertEquals(ScanOutcome.PAYMENT_PENDING, result.getOutcome());
        assertEquals(Long.valueOf(24), result.getShortfall());
        assertEquals("PAYMENT_PENDING", f.journeyOf("MLK-PENDING").status());
        assertEquals(45, f.balanceOf(2));
        assertEquals(0, f.chargeCount(2));
    }

    @Test
    public void afterATopUpSettlingCompletesTheJourneyAndUsesTheTicket() {
        f.topUp(2, 100, AFTER_TOP_UP);                                                           // 145

        ScanResult result = f.journeyService.settle(pendingJourneyId, AFTER_TOP_UP);

        assertEquals(ScanOutcome.JOURNEY_COMPLETED, result.getOutcome());
        assertEquals(Long.valueOf(69), result.getFareCharged());
        assertEquals(Long.valueOf(76), result.getBalance());
        Journey journey = f.journeyOf("MLK-PENDING");
        assertEquals("COMPLETED", journey.status());
        assertEquals(Long.valueOf(69), journey.getFareCharged());
        assertEquals("USED", f.ticket("MLK-PENDING").status());
        assertEquals(76, f.balanceOf(2));
        assertEquals(1, f.chargeCount(2));
    }

    @Test
    public void settlingAgainChargesNothingMore() {
        f.topUp(2, 100, AFTER_TOP_UP);
        f.journeyService.settle(pendingJourneyId, AFTER_TOP_UP);

        ScanResult again = f.journeyService.settle(pendingJourneyId, AFTER_TOP_UP);

        assertEquals(ScanOutcome.ALREADY_SETTLED, again.getOutcome());
        assertEquals(76, f.balanceOf(2));
        assertEquals(1, f.chargeCount(2));
    }

    @Test
    public void scanningOutAgainAfterATopUpAlsoSettlesTheSameFare() {
        f.topUp(2, 100, AFTER_TOP_UP);

        ScanResult result = f.out("MLK-PENDING", "S2", AFTER_TOP_UP);                          // a different stop is ignored

        assertEquals(ScanOutcome.JOURNEY_COMPLETED, result.getOutcome());
        assertEquals("the fare already worked out stays", Long.valueOf(69), result.getFareCharged());
        assertEquals("S5", f.journeyOf("MLK-PENDING").getExitStopId());
    }

    @Test
    public void scanningOutAgainWithoutATopUpChangesNothing() {
        ScanResult result = f.out("MLK-PENDING", "S5", AFTER_TOP_UP);

        assertEquals(ScanOutcome.PAYMENT_PENDING, result.getOutcome());
        assertEquals(0, f.chargeCount(2));
        assertNull(f.journeyOf("MLK-PENDING").getFareCharged());
    }

    @Test
    public void aJourneyThatHasNotEndedCannotBeSettled() {
        f.in("MLK-HAPPY01", "S1", Fixture.MORNING);
        long openJourney = f.journeyOf("MLK-HAPPY01").getJourneyId();

        try {
            f.journeyService.settle(openJourney, AFTER_TOP_UP);
            fail("expected ILLEGAL_TRANSITION");
        } catch (DomainException expected) {
            assertEquals("ILLEGAL_TRANSITION", expected.code());
            assertEquals(409, expected.status());
        }
        assertEquals("IN_PROGRESS", f.journeyOf("MLK-HAPPY01").status());
        assertEquals(500, f.balanceOf(1));
    }

    @Test
    public void settlingAJourneyThatDoesNotExistIsNotFound() {
        try {
            f.journeyService.settle(999, AFTER_TOP_UP);
            fail("expected NotFoundException");
        } catch (NotFoundException expected) {
            assertEquals(404, expected.status());
        }
    }
}
