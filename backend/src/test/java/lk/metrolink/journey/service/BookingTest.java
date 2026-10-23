package lk.metrolink.journey.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import lk.metrolink.journey.domain.BookingRequest;
import lk.metrolink.journey.domain.InvalidBookingException;
import lk.metrolink.journey.domain.NotFoundException;
import lk.metrolink.journey.domain.QRTicket;
import lk.metrolink.journey.fare.FareCalculator;
import lk.metrolink.journey.repository.OnEveryStore;
import lk.metrolink.journey.repository.TicketRepository;
import lk.metrolink.journey.service.view.BookingResult;
import lk.metrolink.journey.service.view.TicketQuote;
import org.junit.Before;
import org.junit.Test;

/**
 * Ticket booking, rules T1 to T12 in docs/rules.md. Runs on every store, so a booking behaves the same in memory and
 * in the database. Each test names the rule it proves.
 */
public class BookingTest extends OnEveryStore {

    private static final LocalDate TODAY = Fixture.TODAY;

    private Fixture f;

    @Before
    public void setUp() {
        f = new Fixture(open(TODAY));
    }

    private static BookingRequest trip(long passenger, String route, String from, String to, LocalDate date) {
        return new BookingRequest(passenger, route, from, to, date);
    }

    private BookingResult book(BookingRequest request) {
        return f.booking.book(request, Fixture.MORNING);
    }

    /** Nimal, resident with LKR 500, books Colombo Fort to Kiribathgoda for today. */
    private BookingResult bookNimalsUsualTrip() {
        return book(trip(1, "R100", "S1", "S5", TODAY));
    }

    private int ticketCount() {
        return f.db.run(() -> f.tickets.list().size());
    }

    private List<String> problemFields(BookingRequest request) {
        try {
            f.booking.book(request, Fixture.MORNING);
            fail("expected InvalidBookingException");
            return List.of();
        } catch (InvalidBookingException expected) {
            return expected.problems().stream().map(BookingRequest.Problem::getField).collect(Collectors.toList());
        }
    }

    // ---- what is refused (T1 to T5)

    @Test
    public void aBookingWithEveryFieldWrongReportsThemAllAndStoresNothing() {                        // T1
        int before = ticketCount();

        assertEquals(List.of("routeNo", "boardingStopId", "destinationStopId", "travelDate"),
                problemFields(trip(1, null, null, null, null)));

        assertEquals(before, ticketCount());
    }

    @Test
    public void aRouteThatDoesNotExistIsRefused() {                                                  // T2
        assertEquals(List.of("routeNo"), problemFields(trip(1, "R999", "S1", "S5", TODAY)));
    }

    @Test
    public void stopsMustBeOnTheRouteAndInOrder() {                                                  // T2
        assertEquals(List.of("destinationStopId"), problemFields(trip(1, "R200", "S1", "S5", TODAY)));
        assertEquals(List.of("destinationStopId"), problemFields(trip(1, "R100", "S5", "S1", TODAY)));
        assertEquals(List.of("destinationStopId"), problemFields(trip(1, "R100", "S3", "S3", TODAY)));
    }

    @Test
    public void theTravelDateMustBeBetweenTodayAndThirtyDaysAhead() {                                // T3
        assertEquals(List.of("travelDate"), problemFields(trip(1, "R100", "S1", "S5", TODAY.minusDays(1))));
        assertEquals(List.of("travelDate"), problemFields(trip(1, "R100", "S1", "S5", TODAY.plusDays(31))));

        assertEquals(TODAY, book(trip(1, "R100", "S1", "S5", TODAY)).getTicket().getTravelDate());
        assertEquals(TODAY.plusDays(30), book(trip(1, "R100", "S1", "S5", TODAY.plusDays(30))).getTicket().getTravelDate());
    }

    @Test
    public void todayIsTheColomboDayNotTheUtcDay() {                                                 // T3, D6
        LocalDate twentieth = TODAY.minusDays(1);
        Instant lateOnTheTwentiethInColombo = Instant.parse("2026-10-20T18:00:00Z");     // 23:30 on the 20th
        Instant justAfterMidnightInColombo = Instant.parse("2026-10-20T19:00:00Z");      // 00:30 on the 21st

        assertEquals(twentieth, f.booking.book(trip(1, "R100", "S1", "S5", twentieth), lateOnTheTwentiethInColombo)
                .getTicket().getTravelDate());
        try {
            f.booking.book(trip(1, "R100", "S1", "S5", twentieth), justAfterMidnightInColombo);
            fail("the 20th is over in Colombo");
        } catch (InvalidBookingException expected) {
            assertEquals("travelDate", expected.problems().get(0).getField());
        }
    }

    @Test
    public void aTouristCannotBookADayAfterTheVisaEnds() {                                           // T4
        // Emma (customer 4) has a visa that ends ten days from today.
        assertEquals(List.of("travelDate"), problemFields(trip(4, "R100", "S1", "S3", TODAY.plusDays(11))));

        assertEquals(TODAY.plusDays(10), book(trip(4, "R100", "S1", "S3", TODAY.plusDays(10))).getTicket().getTravelDate());
    }

    @Test
    public void aTouristWhoseVisaHasEndedCannotBookAtAll() {                                         // T4
        // Hiro (customer 5) had a visa that ended yesterday.
        assertEquals(List.of("travelDate"), problemFields(trip(5, "R100", "S1", "S3", TODAY)));
    }

    @Test
    public void aResidentCanBookAnyDayInTheWindow() {                                                // T4
        assertEquals(TODAY.plusDays(30), book(trip(1, "R100", "S1", "S5", TODAY.plusDays(30))).getTicket().getTravelDate());
    }

    @Test
    public void aCustomerWhoDoesNotExistCannotBook() {                                               // T5
        try {
            book(trip(99, "R100", "S1", "S5", TODAY));
            fail("expected NotFoundException");
        } catch (NotFoundException expected) {
            assertEquals(404, expected.status());
            assertTrue(expected.getMessage().contains("Passenger 99"));
        }
    }

    // ---- the fare (T6)

    @Test
    public void theQuotedFareIsWhatTheFareCalculatorGivesForTheBookedStops() {                       // T6
        assertEquals(69, book(trip(1, "R100", "S1", "S5", TODAY)).getTicket().getQuotedFare());
        assertEquals(54, book(trip(1, "R100", "S1", "S4", TODAY)).getTicket().getQuotedFare());
        assertEquals(60, book(trip(1, "R100", "S2", "S5", TODAY)).getTicket().getQuotedFare());
    }

    @Test
    public void aFlatRouteQuotesItsFlatAmountForAnyTwoStops() {                                      // T6
        assertEquals(30, book(trip(1, "R200", "S1", "S3", TODAY)).getTicket().getQuotedFare());
        assertEquals(30, book(trip(1, "R200", "S1", "S2", TODAY)).getTicket().getQuotedFare());
    }

    @Test
    public void theFareChargedAtTheExitIsTheFareThatWasQuotedAtBooking() {                           // T6, T12
        String qr = bookNimalsUsualTrip().getTicket().getQrCode();

        f.in(qr, "S1", Fixture.MORNING);
        ScanResult out = f.out(qr, "S5", Fixture.LATER);

        assertEquals(Long.valueOf(69), out.getFareCharged());
        assertEquals(431, f.balanceOf(1));
    }

    // ---- what a booking creates (T7 to T10)

    @Test
    public void bookingTakesNoMoneyAndMakesAnActiveTicketWithNoJourney() {                           // T7
        BookingResult result = bookNimalsUsualTrip();

        assertEquals(500, f.balanceOf(1));
        assertEquals(0, f.chargeCount(1));
        QRTicket stored = f.ticket(result.getTicket().getQrCode());
        assertEquals("ACTIVE", stored.status());
        assertEquals(Fixture.MORNING, stored.getIssuedAt());
        assertEquals(TODAY, stored.getTravelDate());
        assertEquals("R100", stored.getRouteNo());
        assertEquals("S1", stored.getBoardingStopId());
        assertEquals("S5", stored.getPlannedDestinationStopId());
        assertEquals(69, stored.getQuotedFare());
        assertTrue(f.db.run(() -> f.journeys.findByTicketId(stored.getTicketId())).isEmpty());
    }

    @Test
    public void everyTicketGetsItsOwnHardToGuessCode() {                                             // T8
        String first = bookNimalsUsualTrip().getTicket().getQrCode();
        String second = bookNimalsUsualTrip().getTicket().getQrCode();

        assertTrue(first, first.matches("MLK-[A-HJ-NP-Z2-9]{8}"));
        assertNotEquals(first, second);
    }

    @Test
    public void aCodeThatIsAlreadyTakenIsNeverReused() {                                             // T8
        f.queuedCodes.addAll(List.of("MLK-HAPPY01", "MLK-OLD0001", "MLK-FRESH001"));

        BookingResult result = bookNimalsUsualTrip();

        assertEquals("MLK-FRESH001", result.getTicket().getQrCode());
        assertEquals("the demo ticket is untouched", "ACTIVE", f.ticket("MLK-HAPPY01").status());
        assertEquals(11, ticketCount());
    }

    @Test
    public void ifEveryCodeDrawnIsTakenTheBookingFailsAndStoresNothing() {                           // T8
        f.queuedCodes.addAll(List.of("MLK-HAPPY01", "MLK-HAPPY01", "MLK-HAPPY01", "MLK-HAPPY01", "MLK-HAPPY01"));

        try {
            bookNimalsUsualTrip();
            fail("expected IllegalStateException");
        } catch (IllegalStateException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("QR code"));
        }

        assertEquals(10, ticketCount());
    }

    @Test
    public void theTicketBelongsToTheCustomerAndIsInTheirListAtOnce() {                              // T9
        BookingResult result = bookNimalsUsualTrip();

        assertEquals(1, result.getTicket().getPassengerId());
        assertEquals("Nimal Perera", result.getTicket().getPassengerName());
        assertEquals(11, result.getTicket().getTicketId());
        assertTrue(f.db.run(() -> f.tickets.listForPassenger(1)).stream()
                .anyMatch(t -> t.getQrCode().equals(result.getTicket().getQrCode())));
        assertTrue(f.queries.tickets().stream().anyMatch(t -> t.getQrCode().equals(result.getTicket().getQrCode())));
        assertTrue("nobody else gets it", f.db.run(() -> f.tickets.listForPassenger(2)).stream()
                .noneMatch(t -> t.getQrCode().equals(result.getTicket().getQrCode())));
    }

    @Test
    public void aBookingThatFailsAfterTheTicketIsWrittenLeavesNothingAndGivesTheIdBack() {           // T9, D5
        TicketRepository failing = new TicketRepository() {
            @Override public Optional<QRTicket> findById(long ticketId) { return f.tickets.findById(ticketId); }
            @Override public Optional<QRTicket> findByQrCode(String qrCode) { return f.tickets.findByQrCode(qrCode); }
            @Override public List<QRTicket> list() { return f.tickets.list(); }
            @Override public List<QRTicket> listForPassenger(long passengerId) { return f.tickets.listForPassenger(passengerId); }
            @Override public QRTicket insert(QRTicket draft) {
                f.tickets.insert(draft);
                throw new IllegalStateException("disk full");
            }
            @Override public void save(QRTicket ticket) { f.tickets.save(ticket); }
        };
        BookingService broken = new BookingService(f.db, f.accounts, failing, f.routes, f.fareRules,
                new FareCalculator(), f.queries, QrCodes::next);

        try {
            broken.book(trip(1, "R100", "S1", "S5", TODAY), Fixture.MORNING);
            fail("expected IllegalStateException");
        } catch (IllegalStateException expected) {
            assertEquals("disk full", expected.getMessage());
        }

        assertEquals(10, ticketCount());
        assertEquals("the id is handed out again", 11, bookNimalsUsualTrip().getTicket().getTicketId());
    }

    @Test
    public void aRefusedBookingDoesNotUseUpAnId() {                                                  // T9
        problemFields(trip(1, "R999", "S1", "S5", TODAY));

        assertEquals(11, bookNimalsUsualTrip().getTicket().getTicketId());
    }

    @Test
    public void aCustomerWhoCannotYetPayIsToldToAddCreditButStillGetsTheTicket() {                   // T10
        // Kasun (customer 3) has LKR 10 and the trip costs 40.
        BookingResult result = book(trip(3, "R100", "S1", "S3", TODAY));

        assertFalse(result.isCanPayNow());
        assertEquals(10, result.getBalance());
        assertTrue(result.getMessage(), result.getMessage().contains("add credit"));
        assertEquals(40, result.getTicket().getQuotedFare());
        assertEquals(11, ticketCount());
    }

    @Test
    public void aCustomerWhoCanPayIsNotTold() {                                                      // T10
        BookingResult result = bookNimalsUsualTrip();

        assertTrue(result.isCanPayNow());
        assertEquals(500, result.getBalance());
        assertFalse(result.getMessage(), result.getMessage().contains("add credit"));
        assertEquals("Ticket booked: Colombo Fort to Kiribathgoda on 21 Oct 2026. The fare is about LKR 69, charged when you get off.",
                result.getMessage());
    }

    // ---- quotes (T11)

    @Test
    public void aQuoteGivesTheFareAndCreatesNothing() {                                              // T11
        TicketQuote quote = f.booking.quote(trip(1, "R100", "S1", "S5", TODAY), Fixture.MORNING);

        assertEquals(69, quote.getFare());
        assertEquals(10.8, quote.getDistanceKm(), 0.0001);
        assertEquals("DISTANCE", quote.getFareType());
        assertEquals(500, quote.getBalance());
        assertTrue(quote.isCanPayNow());
        assertEquals(10, ticketCount());
    }

    @Test
    public void aQuoteForAFlatRouteSaysFlat() {                                                      // T11
        TicketQuote quote = f.booking.quote(trip(1, "R200", "S1", "S3", TODAY), Fixture.MORNING);

        assertEquals(30, quote.getFare());
        assertEquals("FLAT", quote.getFareType());
    }

    @Test
    public void aQuoteIsRefusedForTheSameReasonsAsABooking() {                                       // T11
        try {
            f.booking.quote(trip(1, "R200", "S1", "S5", TODAY.minusDays(2)), Fixture.MORNING);
            fail("expected InvalidBookingException");
        } catch (InvalidBookingException expected) {
            assertEquals(List.of("destinationStopId", "travelDate"),
                    expected.problems().stream().map(BookingRequest.Problem::getField).collect(Collectors.toList()));
        }
        try {
            f.booking.quote(trip(99, "R100", "S1", "S5", TODAY), Fixture.MORNING);
            fail("expected NotFoundException");
        } catch (NotFoundException expected) {
            assertEquals(404, expected.status());
        }
    }

    // ---- the booked ticket at the reader (T12)

    @Test
    public void aTicketBookedForTodayCanBeTappedInToday() {                                          // T12
        String qr = bookNimalsUsualTrip().getTicket().getQrCode();

        ScanResult in = f.in(qr, "S1", Fixture.MORNING);

        assertEquals(ScanOutcome.JOURNEY_STARTED, in.getOutcome());
    }

    @Test
    public void aTicketBookedForTomorrowIsRefusedToday() {                                           // T12, B2
        String qr = book(trip(1, "R100", "S1", "S5", TODAY.plusDays(1))).getTicket().getQrCode();

        ScanResult in = f.in(qr, "S1", Fixture.MORNING);

        assertEquals(ScanOutcome.REJECTED, in.getOutcome());
        assertEquals(RejectionReason.WRONG_TRAVEL_DATE, in.getReason());
    }

    @Test
    public void aTouristsTicketBookedForTheLastDayOfTheVisaWorksOnThatDay() {                       // T4, T12, A2
        // Emma (customer 4) has a visa that ends ten days from today.
        String qr = book(trip(4, "R100", "S1", "S3", TODAY.plusDays(10))).getTicket().getQrCode();
        Instant lastDayMorning = Instant.parse("2026-10-31T04:00:00Z");                          // 09:30 on the 31st, Colombo

        ScanResult in = f.in(qr, "S1", lastDayMorning);

        assertEquals(ScanOutcome.JOURNEY_STARTED, in.getOutcome());
    }
}
