package lk.metrolink.journey.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.time.Instant;
import lk.metrolink.journey.domain.QRTicket;
import lk.metrolink.journey.domain.TicketState;
import org.junit.Before;
import org.junit.Test;

/** Rules B1 to B6, plus D6 (dates are Colombo dates). */
public class BoardingTest {

    private Fixture f;

    @Before
    public void setUp() {
        f = new Fixture();
    }

    private void assertRejected(ScanResult result, RejectionReason reason) {
        assertEquals(ScanOutcome.REJECTED, result.getOutcome());
        assertEquals(reason, result.getReason());
    }

    @Test
    public void aTokenThatMatchesNoTicketIsRejected() {                                          // B1
        ScanResult result = f.in("NOT-A-TICKET", "S1", Fixture.MORNING);

        assertRejected(result, RejectionReason.UNKNOWN_TOKEN);
        assertNull(result.getTicket());
        assertEquals(0, f.journeyCount());
    }

    @Test
    public void anExpiredAFutureAndACancelledTicketAreRejectedForTheirOwnReasons() {             // B2
        assertRejected(f.in("MLK-OLD0001", "S1", Fixture.MORNING), RejectionReason.TICKET_EXPIRED);
        assertRejected(f.in("MLK-FUTURE1", "S1", Fixture.MORNING), RejectionReason.WRONG_TRAVEL_DATE);
        assertRejected(f.in("MLK-CANCEL01", "S1", Fixture.MORNING), RejectionReason.TICKET_CANCELLED);
        assertEquals(0, f.journeyCount());
    }

    @Test
    public void aTicketThatIsAlreadyUsedIsRejected() {                                           // B2
        f.db.execute(() -> {
            QRTicket ticket = f.tickets.findByQrCode("MLK-HAPPY01").orElseThrow();
            ticket.markUsed();
            f.tickets.save(ticket);
        });

        assertRejected(f.in("MLK-HAPPY01", "S1", Fixture.MORNING), RejectionReason.TICKET_ALREADY_USED);
    }

    @Test
    public void aTicketCannotOpenASecondJourneyWhileTheFirstIsOpen() {                           // B3
        f.in("MLK-HAPPY01", "S1", Fixture.MORNING);

        assertRejected(f.in("MLK-HAPPY01", "S1", Fixture.LATER), RejectionReason.ALREADY_BOARDED);
        assertEquals(1, f.journeyCount());
    }

    @Test
    public void aTicketThatHasBeenThroughAFullJourneyCannotBoardAgain() {                        // B3
        f.in("MLK-HAPPY01", "S1", Fixture.MORNING);
        f.out("MLK-HAPPY01", "S5", Fixture.LATER);

        assertRejected(f.in("MLK-HAPPY01", "S1", Fixture.LATER), RejectionReason.TICKET_ALREADY_USED);
        assertEquals(1, f.journeyCount());
    }

    @Test
    public void aTicketWhoseFareIsStillOwedCannotBoardAgainEither() {                            // B3
        f.in("MLK-PENDING", "S1", Fixture.MORNING);
        f.out("MLK-PENDING", "S5", Fixture.LATER);                                              // too little credit

        assertRejected(f.in("MLK-PENDING", "S1", Fixture.LATER), RejectionReason.TICKET_ALREADY_USED);
    }

    @Test
    public void theBoardingStopMustBeOnTheRouteAndBeforeThePlannedDestination() {               // B4
        assertRejected(f.in("MLK-HAPPY01", "S5", Fixture.MORNING), RejectionReason.INVALID_BOARDING_STOP); // is the destination
        assertRejected(f.in("MLK-HAPPY01", "S6", Fixture.MORNING), RejectionReason.INVALID_BOARDING_STOP); // after it
        assertRejected(f.in("MLK-FLAT001", "S5", Fixture.MORNING), RejectionReason.INVALID_BOARDING_STOP); // not on R200
        assertRejected(f.in("MLK-HAPPY01", "NOWHERE", Fixture.MORNING), RejectionReason.INVALID_BOARDING_STOP);
        assertEquals(0, f.journeyCount());
    }

    @Test
    public void aPassengerWhoCannotCoverThePlannedFareIsRefusedAndNothingIsRecorded() {          // B5
        ScanResult result = f.in("MLK-NOFUNDS", "S1", Fixture.MORNING);

        assertRejected(result, RejectionReason.INSUFFICIENT_CREDIT);
        assertEquals(Long.valueOf(40), result.getRequired());
        assertEquals(Long.valueOf(10), result.getBalance());
        assertEquals(Long.valueOf(30), result.getShortfall());
        assertEquals(0, f.journeyCount());
        assertEquals(10, f.balanceOf(3));
    }

    @Test
    public void exactlyEnoughCreditIsEnoughToBoard() {                                           // B5
        f.topUp(3, 30, Fixture.MORNING);                                                         // 10 + 30 = 40

        assertEquals(ScanOutcome.JOURNEY_STARTED, f.in("MLK-NOFUNDS", "S1", Fixture.MORNING).getOutcome());
    }

    @Test
    public void theBoardingCheckPricesTheTripFromWhereThePassengerActuallyBoards() {             // B5
        ScanResult result = f.in("MLK-HAPPY01", "S2", Fixture.MORNING);                          // S2 to S5

        assertEquals(ScanOutcome.JOURNEY_STARTED, result.getOutcome());
        assertEquals(Long.valueOf(60), result.getRequired());
    }

    @Test
    public void boardingOpensAJourneyAndMovesNoMoney() {                                         // B6
        ScanResult result = f.in("MLK-HAPPY01", "S1", Fixture.MORNING);

        assertEquals(ScanOutcome.JOURNEY_STARTED, result.getOutcome());
        assertNotNull(result.getJourney());
        assertEquals("IN_PROGRESS", f.journeyOf("MLK-HAPPY01").status());
        assertEquals("S1", f.journeyOf("MLK-HAPPY01").getBoardingStopId());
        assertEquals(Fixture.MORNING, f.journeyOf("MLK-HAPPY01").getTapInTime());
        assertEquals("ACTIVE", f.ticket("MLK-HAPPY01").status());
        assertEquals(500, f.balanceOf(1));
        assertEquals(0, f.chargeCount(1));
        assertEquals(Long.valueOf(69), result.getRequired());
        assertEquals(Long.valueOf(500), result.getBalance());
    }

    @Test
    public void aTicketIsJudgedByTheColomboDateNotTheUtcDate() {                                 // D6
        Instant justAfterColomboMidnight = Instant.parse("2026-10-20T19:00:00Z");               // 00:30 on the 21st
        Instant justBeforeColomboMidnight = Instant.parse("2026-10-20T18:00:00Z");              // 23:30 on the 20th

        assertEquals(ScanOutcome.JOURNEY_STARTED, f.in("MLK-HAPPY01", "S1", justAfterColomboMidnight).getOutcome());
        assertRejected(f.in("MLK-FLAT001", "S1", justBeforeColomboMidnight), RejectionReason.WRONG_TRAVEL_DATE);
    }

    @Test
    public void aTicketRefusedAtBoardingStillCarriesTheTicketForTheDisplay() {
        ScanResult result = f.in("MLK-OLD0001", "S1", Fixture.MORNING);

        assertTrue(result.getTicket() != null && result.getTicket().getQrCode().equals("MLK-OLD0001"));
        assertEquals(TicketState.of("ACTIVE").name(), result.getTicket().status());
    }
}
