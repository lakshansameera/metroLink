package lk.metrolink.journey.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.time.Instant;
import org.junit.Before;
import org.junit.Test;

/** Rules A1 to A4 as the passenger meets them at the reader. */
public class AccountKindsTest {

    private Fixture f;

    @Before
    public void setUp() {
        f = new Fixture();
    }

    @Test
    public void aTouristWithAValidVisaTravelsLikeAnyoneElse() {                                  // A1, A2
        f.in("MLK-TOURIST1", "S1", Fixture.MORNING);

        ScanResult result = f.out("MLK-TOURIST1", "S3", Fixture.LATER);

        assertEquals(ScanOutcome.JOURNEY_COMPLETED, result.getOutcome());
        assertEquals(Long.valueOf(40), result.getFareCharged());
        assertEquals(260, f.balanceOf(4));
    }

    @Test
    public void aTouristWhoseVisaHasExpiredCannotBoardEvenWithCredit() {                         // A2, A4
        ScanResult result = f.in("MLK-VISAEXP1", "S1", Fixture.MORNING);

        assertEquals(ScanOutcome.REJECTED, result.getOutcome());
        assertEquals(RejectionReason.ACCOUNT_EXPIRED, result.getReason());
        assertEquals("the credit is still there", 300, f.balanceOf(5));
        assertEquals(0, f.journeyCount());
    }

    @Test
    public void theVisaExpiryDayItselfStillWorksUntilColomboMidnight() {                         // A2
        Instant lastMinuteOfVisaDay = Instant.parse("2026-10-31T18:29:00Z");                     // 23:59 on 31 October
        Instant firstMinuteAfter = Instant.parse("2026-10-31T18:30:00Z");                        // 00:00 on 1 November

        // MLK-TOURIST1 is for 21 October, so use the account directly through a ticket for that day:
        // the visa (valid through 31 October) is what is under test, not the ticket date.
        f.in("MLK-TOURIST1", "S1", Fixture.MORNING);
        ScanResult lastMinute = f.out("MLK-TOURIST1", "S3", lastMinuteOfVisaDay);

        assertEquals(ScanOutcome.JOURNEY_COMPLETED, lastMinute.getOutcome());

        Fixture other = new Fixture();
        other.in("MLK-TOURIST1", "S1", Fixture.MORNING);
        ScanResult tooLate = other.out("MLK-TOURIST1", "S3", firstMinuteAfter);

        assertEquals(ScanOutcome.PAYMENT_PENDING, tooLate.getOutcome());
    }

    @Test
    public void aVisaThatExpiresDuringTheJourneyLeavesTheFareOwedAndTheCreditUntouched() {      // A2, A4
        f.in("MLK-TOURIST1", "S1", Fixture.MORNING);

        ScanResult result = f.out("MLK-TOURIST1", "S3", Instant.parse("2026-11-01T04:00:00Z"));

        assertEquals(ScanOutcome.PAYMENT_PENDING, result.getOutcome());
        assertEquals(RejectionReason.ACCOUNT_EXPIRED, result.getReason());
        assertEquals(Long.valueOf(40), result.getFareDue());
        assertNull(result.getShortfall());
        assertEquals(300, f.balanceOf(4));
        assertEquals(0, f.chargeCount(4));
        assertEquals("PAYMENT_PENDING", f.journeyOf("MLK-TOURIST1").status());
    }

    @Test
    public void aResidentsAccountNeverExpires() {                                                // A3
        f.in("MLK-HAPPY01", "S1", Fixture.MORNING);

        ScanResult result = f.out("MLK-HAPPY01", "S5", Instant.parse("2040-01-01T04:00:00Z"));

        assertEquals(ScanOutcome.JOURNEY_COMPLETED, result.getOutcome());
    }
}
