package lk.metrolink.journey;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import lk.metrolink.journey.domain.QRTicket;
import lk.metrolink.journey.domain.Route;
import lk.metrolink.journey.domain.RouteStop;
import lk.metrolink.journey.domain.TripComparison;
import lk.metrolink.journey.domain.TripComparison.Kind;
import org.junit.Test;

/** The real trip against the ticket (rules X7, X9, X10). The ticket in every test is S3 to S5 and cost LKR 40. */
public class TripComparisonTest {

    private static final Route R100 = new Route("R100", "Colombo Fort - Ganemulla", List.of(
            new RouteStop("S1", 1, 0), new RouteStop("S2", 2, 2.1), new RouteStop("S3", 3, 4.3),
            new RouteStop("S4", 4, 7.4), new RouteStop("S5", 5, 10.8), new RouteStop("S6", 6, 14.5),
            new RouteStop("S7", 7, 22.0)));

    private static final QRTicket TICKET = QRTicket.issue("MLK-TRIP", 1, "R100", "S3", "S5",
            LocalDate.parse("2026-10-21"), 40, Instant.parse("2026-10-21T01:00:00Z"));

    private static TripComparison trip(String boardedAt, String exitedAt) {
        return TripComparison.of(R100, TICKET, boardedAt, exitedAt);
    }

    // ---- the kind

    @Test
    public void theTicketsOwnStopsAreAsTicketed() {
        TripComparison trip = trip("S3", "S5");

        assertEquals(Kind.AS_TICKETED, trip.kind());
        assertFalse(trip.boardedOffTicket());
        assertFalse(trip.exitedOffTicket());
        assertFalse(trip.isOverTravel());
    }

    @Test
    public void gettingOffAfterThePlannedStopIsOverTravel() {                                         // X7
        TripComparison trip = trip("S3", "S6");

        assertEquals(Kind.OVER_TRAVEL, trip.kind());
        assertFalse(trip.boardedOffTicket());
        assertTrue(trip.exitedOffTicket());
    }

    @Test
    public void boardingBeforeTheTicketsStopIsOverTravelToo() {                                       // X9
        TripComparison trip = trip("S2", "S5");

        assertEquals(Kind.OVER_TRAVEL, trip.kind());
        assertTrue(trip.boardedOffTicket());
        assertFalse(trip.exitedOffTicket());
    }

    @Test
    public void boardingElsewhereAndGettingOffElsewhereBothWayOutIsOverTravel() {                     // the case that was reported
        TripComparison trip = trip("S2", "S6");

        assertEquals(Kind.OVER_TRAVEL, trip.kind());
        assertTrue(trip.boardedOffTicket());
        assertTrue(trip.exitedOffTicket());
    }

    @Test
    public void boardingLaterButGettingOffAfterThePlannedStopStillWentOutsideTheTicket() {
        TripComparison trip = trip("S4", "S6");

        assertEquals("the last part of the ride is outside the ticket", Kind.OVER_TRAVEL, trip.kind());
        assertTrue(trip.boardedOffTicket());
    }

    @Test
    public void stayingInsideTheTicketsStretchButAtDifferentStopsIsShorter() {
        assertEquals(Kind.SHORTER, trip("S4", "S5").kind());                                          // boarded later
        assertEquals(Kind.SHORTER, trip("S3", "S4").kind());                                          // got off earlier
    }

    // ---- still on the bus

    @Test
    public void whileOnTheBusNothingIsDecidedUnlessTheTicketIsAlreadyOvershot() {
        assertNull(trip("S3", null).kind());
        assertNull("boarding later cannot be judged until the exit", trip("S4", null).kind());
        assertEquals("boarding before the ticket's stop is already over-travel",
                Kind.OVER_TRAVEL, trip("S2", null).kind());
        assertFalse(trip("S3", null).exitedOffTicket());
    }

    // ---- the fare against the ticket's fare

    @Test
    public void aHigherFareThanTheTicketIsOverCharged() {                                             // X10
        TripComparison trip = trip("S3", "S6");

        assertEquals(Long.valueOf(17), trip.fareDifference(57L));
        assertTrue(trip.isOverCharged(57L));
    }

    @Test
    public void aLowerFareIsNotOverChargedAndSaysHowMuchLess() {
        TripComparison trip = trip("S4", "S5");

        assertEquals(Long.valueOf(-10), trip.fareDifference(30L));
        assertFalse(trip.isOverCharged(30L));
    }

    @Test
    public void theSameFareIsNoDifference() {
        TripComparison trip = trip("S3", "S5");

        assertEquals(Long.valueOf(0), trip.fareDifference(40L));
        assertFalse(trip.isOverCharged(40L));
    }

    @Test
    public void beforeTheFareIsWorkedOutThereIsNoDifference() {
        TripComparison trip = trip("S3", null);

        assertNull(trip.fareDifference(null));
        assertFalse(trip.isOverCharged(null));
        assertEquals(40, trip.quotedFare());
    }

    // ---- the words for the console log

    @Test
    public void theSummaryNamesTheStopsTheKindAndTheMoney() {
        String summary = trip("S2", "S6").summary(57L);

        assertTrue(summary, summary.startsWith("ticket S3 to S5, trip S2 to S6: OVER_TRAVEL"));
        assertTrue(summary, summary.contains("boarded at another stop than the ticket"));
        assertTrue(summary, summary.contains("got off at another stop than the ticket"));
        assertTrue(summary, summary.contains("fare LKR 57 against the ticket's LKR 40 (LKR 17 more, over-charged)"));
    }

    @Test
    public void theSummaryOfAShorterTripSaysHowMuchLess() {
        assertTrue(trip("S4", "S5").summary(30L).contains("(LKR 10 less)"));
    }

    @Test
    public void theSummaryBeforeTheExitSaysNothingIsDecided() {
        assertEquals("ticket S3 to S5, trip S3 to ?: not decided yet", trip("S3", null).summary(null));
    }

    @Test
    public void theKindsAreNamedExactlyAsTheyAreSentToTheScreen() {
        assertEquals(List.of("AS_TICKETED", "SHORTER", "OVER_TRAVEL"), Kind.names());
        assertEquals("OVER_TRAVEL", trip("S3", "S6").kindName());
        assertNull(trip("S3", null).kindName());
    }
}
