package lk.metrolink.journey.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.List;
import lk.metrolink.journey.domain.NotFoundException;
import lk.metrolink.journey.repository.OnEveryStore;
import lk.metrolink.journey.service.ReaderDisplay.Tone;
import lk.metrolink.journey.service.view.JourneyView;
import lk.metrolink.journey.service.view.PassengerView;
import org.junit.Before;
import org.junit.Test;

/** The reader turns results into a tone and a sentence; the query service names things for the screens. */
public class ReaderServiceTest extends OnEveryStore {

    private Fixture f;
    private ReaderService reader;
    private QueryService queries;
    private TestDataService testData;

    @Before
    public void setUp() {
        f = new Fixture(open(Fixture.TODAY));
        queries = new QueryService(f.db, f.passengers, f.accounts, f.tickets, f.journeys, f.transactions, f.routes);
        reader = new ReaderService(f.db, f.journeyService, f.routes, queries);
        testData = new TestDataService(f.db, f.accountService, f.accounts, queries, () -> f.store.reset(Fixture.TODAY));
    }

    private ReaderDisplay tap(String qr, ScanDirection direction, String stop) {
        return reader.scan(new ScanRequest(qr, direction, stop,
                direction == ScanDirection.IN ? Fixture.MORNING : Fixture.LATER));
    }

    @Test
    public void boardingIsASuccessThatSaysTheFareComesLater() {
        ReaderDisplay shown = tap("MLK-HAPPY01", ScanDirection.IN, "S1");

        assertEquals(Tone.SUCCESS, shown.getTone());
        assertEquals(ScanOutcome.JOURNEY_STARTED, shown.getOutcome());
        assertTrue(shown.getMessage().contains("charged when you get off"));
        assertEquals("IN_PROGRESS", shown.getJourney().getStatus());
        assertEquals("Colombo Fort", shown.getJourney().getBoardingStopName());
        assertEquals(Long.valueOf(69), shown.getFare().getRequired());
    }

    @Test
    public void aCompletedJourneyShowsWhatWasChargedAndWhatIsLeft() {
        tap("MLK-HAPPY01", ScanDirection.IN, "S1");

        ReaderDisplay shown = tap("MLK-HAPPY01", ScanDirection.OUT, "S5");

        assertEquals(Tone.SUCCESS, shown.getTone());
        assertEquals("Fare LKR 69 charged. Balance LKR 431.", shown.getMessage());
        assertEquals(Long.valueOf(69), shown.getFare().getCharged());
        assertEquals("COMPLETED", shown.getJourney().getStatus());
        assertEquals("Kiribathgoda", shown.getJourney().getExitStopName());
        assertEquals("USED", shown.getTicket().getStatus());
        assertFalse(shown.isOvertravelled());
    }

    @Test
    public void overtravelIsMentionedButStaysASuccess() {
        tap("MLK-OVERTR1", ScanDirection.IN, "S1");

        ReaderDisplay shown = tap("MLK-OVERTR1", ScanDirection.OUT, "S5");

        assertEquals(Tone.SUCCESS, shown.getTone());
        assertTrue(shown.isOvertravelled());
        assertTrue(shown.getJourney().isOvertravelled());
        assertTrue(shown.getMessage(), shown.getMessage().endsWith(
                " Over-travel: your ticket was Colombo Fort to Peliyagoda, you travelled Colombo Fort to Kiribathgoda."
                        + " Over-charged: LKR 15 more than the ticket fare of LKR 54."));
    }

    @Test
    public void boardingAtAnotherStopThanTheTicketIsLetThroughWithAWarning() {                     // X9
        ReaderDisplay shown = tap("MLK-HAPPY01", ScanDirection.IN, "S2");                         // the ticket says S1

        assertEquals(ScanOutcome.JOURNEY_STARTED, shown.getOutcome());
        assertEquals(Tone.WARNING, shown.getTone());
        assertTrue(shown.getMessage(), shown.getMessage().startsWith(
                "Journey started at Maradana, not Colombo Fort as on your ticket."));
        assertEquals("IN_PROGRESS", shown.getJourney().getStatus());
        assertTrue(shown.getJourney().getTrip().isBoardedOffTicket());
        assertNull("nothing is decided until the exit", shown.getJourney().getTrip().getKind());
        assertNull(shown.getJourney().getTrip().getFareDifference());
    }

    @Test
    public void boardingAtTheTicketsOwnStopStaysAPlainSuccess() {
        ReaderDisplay shown = tap("MLK-HAPPY01", ScanDirection.IN, "S1");

        assertEquals(Tone.SUCCESS, shown.getTone());
        assertFalse(shown.getJourney().getTrip().isBoardedOffTicket());
    }

    @Test
    public void gettingOffEarlierIsAShorterTripAndSaysHowMuchLess() {                              // X10
        tap("MLK-HAPPY01", ScanDirection.IN, "S1");

        ReaderDisplay shown = tap("MLK-HAPPY01", ScanDirection.OUT, "S4");

        assertEquals(Tone.SUCCESS, shown.getTone());
        assertFalse(shown.isOvertravelled());
        assertEquals("SHORTER", shown.getJourney().getTrip().getKind());
        assertFalse(shown.getJourney().getTrip().isOvercharged());
        assertEquals(Long.valueOf(54 - 69), shown.getJourney().getTrip().getFareDifference());
        assertEquals("Fare LKR 54 charged. Balance LKR 446. Shorter trip: your ticket was Colombo Fort to Kiribathgoda,"
                + " you travelled Colombo Fort to Peliyagoda. LKR 15 less than the ticket fare of LKR 69.",
                shown.getMessage());
    }

    @Test
    public void boardingElsewhereAndGettingOffElsewhereIsOverTravelWithTheFareAgainstTheTicket() {  // X7, X9, X10
        tap("MLK-HAPPY01", ScanDirection.IN, "S2");                                                // ticket: S1 to S5

        ReaderDisplay shown = tap("MLK-HAPPY01", ScanDirection.OUT, "S6");

        assertEquals(Tone.SUCCESS, shown.getTone());
        assertTrue(shown.isOvertravelled());
        assertEquals("OVER_TRAVEL", shown.getJourney().getTrip().getKind());
        assertTrue(shown.getJourney().getTrip().isBoardedOffTicket());
        assertTrue(shown.getJourney().getTrip().isExitedOffTicket());
        assertEquals(69, shown.getJourney().getTrip().getQuotedFare());
        assertTrue(shown.getMessage(), shown.getMessage().contains(
                " Over-travel: your ticket was Colombo Fort to Kiribathgoda, you travelled Maradana to Kadawatha."));
    }

    @Test
    public void scanningOutAgainOnAnUnpaidJourneyStillSaysItWasOverTravel() {
        tap("MLK-PENDING", ScanDirection.IN, "S1");                                                // ticket: S1 to S3
        tap("MLK-PENDING", ScanDirection.OUT, "S5");

        ReaderDisplay again = tap("MLK-PENDING", ScanDirection.OUT, "S5");

        assertEquals(ScanOutcome.PAYMENT_PENDING, again.getOutcome());
        assertTrue(again.isOvertravelled());
        assertTrue(again.getMessage(), again.getMessage().contains("Over-travel: your ticket was Colombo Fort to Dematagoda"));
    }

    @Test
    public void aFareThatCannotBePaidIsAWarningThatSaysHowMuchToTopUp() {
        tap("MLK-PENDING", ScanDirection.IN, "S1");

        ReaderDisplay shown = tap("MLK-PENDING", ScanDirection.OUT, "S5");

        assertEquals(Tone.WARNING, shown.getTone());
        assertEquals("Fare LKR 69 is owed. Top up at least LKR 24 to complete this journey."
                + " Over-travel: your ticket was Colombo Fort to Dematagoda, you travelled Colombo Fort to Kiribathgoda."
                + " Over-charged: LKR 29 more than the ticket fare of LKR 40.", shown.getMessage());
        assertEquals(Long.valueOf(24), shown.getFare().getShortfall());
        assertNull(shown.getFare().getCharged());
    }

    @Test
    public void aRefusedBoardingIsAnErrorWithTheNumbers() {
        ReaderDisplay shown = tap("MLK-NOFUNDS", ScanDirection.IN, "S1");

        assertEquals(Tone.ERROR, shown.getTone());
        assertEquals(RejectionReason.INSUFFICIENT_CREDIT, shown.getReason());
        assertEquals("Not enough credit to board: this trip needs LKR 40 and your balance is LKR 10. Please top up.",
                shown.getMessage());
        assertNull("no journey was opened", shown.getJourney());
        assertEquals("MLK-NOFUNDS", shown.getTicket().getQrCode());
    }

    @Test
    public void aRepeatedExitScanIsAWarningThatNothingMoreWasCharged() {
        tap("MLK-HAPPY01", ScanDirection.IN, "S1");
        tap("MLK-HAPPY01", ScanDirection.OUT, "S5");

        ReaderDisplay shown = tap("MLK-HAPPY01", ScanDirection.OUT, "S5");

        assertEquals(Tone.WARNING, shown.getTone());
        assertEquals(ScanOutcome.ALREADY_SETTLED, shown.getOutcome());
        assertTrue(shown.getMessage().contains("Nothing more was charged"));
    }

    @Test
    public void everyRefusalHasItsOwnSentenceAndIsAnError() {
        assertRefusal("This QR code is not recognised.", tap("NOT-A-TICKET", ScanDirection.IN, "S1"));
        assertRefusal("This ticket has expired.", tap("MLK-OLD0001", ScanDirection.IN, "S1"));
        assertRefusal("This ticket is for a different day.", tap("MLK-FUTURE1", ScanDirection.IN, "S1"));
        assertRefusal("This ticket was cancelled.", tap("MLK-CANCEL01", ScanDirection.IN, "S1"));
        assertRefusal("You cannot board here with this ticket.", tap("MLK-HAPPY01", ScanDirection.IN, "S6"));
        assertRefusal("This account has expired, so the journey cannot start.",
                tap("MLK-VISAEXP1", ScanDirection.IN, "S1"));
        assertRefusal("There is no boarding scan for this ticket.", tap("MLK-FLAT001", ScanDirection.OUT, "S3"));

        tap("MLK-HAPPY01", ScanDirection.IN, "S1");
        assertRefusal("This ticket already has a journey in progress.", tap("MLK-HAPPY01", ScanDirection.IN, "S1"));
        assertRefusal("This is not a valid stop to get off at on your journey. Nothing was charged.",
                tap("MLK-HAPPY01", ScanDirection.OUT, "S1"));
        tap("MLK-HAPPY01", ScanDirection.OUT, "S5");
        assertRefusal("This ticket has already been used.", tap("MLK-HAPPY01", ScanDirection.IN, "S1"));
    }

    private static void assertRefusal(String sentence, ReaderDisplay shown) {
        assertEquals(ScanOutcome.REJECTED, shown.getOutcome());
        assertEquals(Tone.ERROR, shown.getTone());
        assertEquals(sentence, shown.getMessage());
    }

    @Test
    public void aStopThatDoesNotExistIsNotFoundRatherThanARejectedPassenger() {
        try {
            tap("MLK-HAPPY01", ScanDirection.IN, "NOWHERE");
            fail("expected NotFoundException");
        } catch (NotFoundException expected) {
            assertEquals(404, expected.status());
        }
    }

    @Test
    public void topUpThenSettleCompletesAPendingJourney() {
        tap("MLK-PENDING", ScanDirection.IN, "S1");
        tap("MLK-PENDING", ScanDirection.OUT, "S5");
        long journeyId = f.journeyOf("MLK-PENDING").getJourneyId();

        PassengerView topped = testData.credit(2, 100);
        ReaderDisplay shown = reader.settle(journeyId);

        assertEquals(145, topped.getBalance());
        assertEquals(ScanOutcome.JOURNEY_COMPLETED, shown.getOutcome());
        assertEquals("Fare LKR 69 charged. Balance LKR 76."
                + " Over-travel: your ticket was Colombo Fort to Dematagoda, you travelled Colombo Fort to Kiribathgoda."
                + " Over-charged: LKR 29 more than the ticket fare of LKR 40.", shown.getMessage());
        assertTrue("settling later still knows it was over-travel", shown.isOvertravelled());
    }

    @Test
    public void queriesNameEverythingForTheScreens() {
        tap("MLK-HAPPY01", ScanDirection.IN, "S1");
        tap("MLK-HAPPY01", ScanDirection.OUT, "S5");

        List<PassengerView> people = queries.passengers();
        List<JourneyView> journeys = queries.journeys();

        assertEquals(5, people.size());
        assertEquals("Nimal Perera", people.get(0).getName());
        assertEquals("PERMANENT", people.get(0).getAccountType());
        assertNull(people.get(0).getVisaExpiry());
        assertEquals("TEMPORARY", people.get(3).getAccountType());
        assertEquals("2026-10-31", String.valueOf(people.get(3).getVisaExpiry()));
        assertEquals(1, journeys.size());
        assertEquals("Nimal Perera", journeys.get(0).getPassengerName());
        assertEquals(10, queries.tickets().size());
        assertEquals(1, queries.statement(1).getCharges().size());
        assertEquals("FARE-1", queries.statement(1).getCharges().get(0).getReference());
        assertEquals(2, queries.reference().getRoutes().size());
        assertEquals(7, queries.reference().getStops().size());
    }

    @Test
    public void askingForSomethingThatDoesNotExistIsNotFound() {
        try {
            queries.journey(99);
            fail("expected NotFoundException");
        } catch (NotFoundException expected) {
            assertEquals("NOT_FOUND", expected.code());
        }
        try {
            queries.statement(99);
            fail("expected NotFoundException");
        } catch (NotFoundException expected) {
            assertEquals(404, expected.status());
        }
    }

    @Test
    public void resetBringsTheDemoDataBack() {
        tap("MLK-HAPPY01", ScanDirection.IN, "S1");
        tap("MLK-HAPPY01", ScanDirection.OUT, "S5");

        testData.reset();

        assertEquals(500, f.balanceOf(1));
        assertEquals(0, f.journeyCount());
    }
}
