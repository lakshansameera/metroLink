package lk.metrolink.journey;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import lk.metrolink.journey.domain.Account;
import lk.metrolink.journey.domain.BookingRequest;
import lk.metrolink.journey.domain.BookingRequest.Problem;
import lk.metrolink.journey.domain.PermanentAccount;
import lk.metrolink.journey.domain.Route;
import lk.metrolink.journey.domain.TemporaryAccount;
import lk.metrolink.journey.repository.DemoData;
import org.junit.Test;

/** What a booking may contain (rules T1 to T4). No database: the request judges itself from the facts it is given. */
public class BookingRequestTest {

    private static final LocalDate TODAY = LocalDate.parse("2026-10-21");

    private static final Route R100 = DemoData.routes().get(0);        // S1 to S7
    private static final Route R200 = DemoData.routes().get(1);        // S1 to S3
    private static final Account RESIDENT = new PermanentAccount(100, 1, 1);

    private static BookingRequest booking(String route, String from, String to, LocalDate date) {
        return new BookingRequest(1, route, from, to, date);
    }

    private static List<String> fields(List<Problem> problems) {
        List<String> names = new ArrayList<>();
        problems.forEach(p -> names.add(p.getField()));
        return names;
    }

    private static String messageFor(List<Problem> problems, String field) {
        return problems.stream().filter(p -> p.getField().equals(field)).findFirst().orElseThrow().getMessage();
    }

    @Test
    public void aCompleteRequestForARealTripHasNoProblems() {
        assertTrue(booking("R100", "S1", "S5", TODAY).problems(R100, RESIDENT, TODAY).isEmpty());
    }

    @Test
    public void everyMissingFieldIsReportedAtOnce() {                                      // T1
        List<Problem> problems = booking(null, null, null, null).problems(null, RESIDENT, TODAY);

        assertEquals(List.of("routeNo", "boardingStopId", "destinationStopId", "travelDate"), fields(problems));
        assertEquals("Route is required", messageFor(problems, "routeNo"));
        assertEquals("Boarding stop is required", messageFor(problems, "boardingStopId"));
        assertEquals("Destination is required", messageFor(problems, "destinationStopId"));
        assertEquals("Travel date is required", messageFor(problems, "travelDate"));
    }

    @Test
    public void blankTextCountsAsMissing() {                                               // T1
        List<Problem> problems = booking("  ", " ", "", TODAY).problems(null, RESIDENT, TODAY);

        assertEquals(List.of("routeNo", "boardingStopId", "destinationStopId"), fields(problems));
    }

    @Test
    public void textAroundTheIdsIsIgnored() {
        assertTrue(booking(" R100 ", " S1 ", "S5 ", TODAY).problems(R100, RESIDENT, TODAY).isEmpty());
    }

    @Test
    public void aRouteThatDoesNotExistIsNamedAndItsStopsAreNotBlamed() {                   // T2
        List<Problem> problems = booking("R999", "S1", "S5", TODAY).problems(null, RESIDENT, TODAY);

        assertEquals(List.of("routeNo"), fields(problems));
        assertEquals("There is no route R999", messageFor(problems, "routeNo"));
    }

    @Test
    public void aStopThatIsNotOnTheRouteIsReported() {                                     // T2
        List<Problem> problems = booking("R200", "S1", "S5", TODAY).problems(R200, RESIDENT, TODAY);

        assertEquals(List.of("destinationStopId"), fields(problems));
        assertEquals("Stop S5 is not on route R200", messageFor(problems, "destinationStopId"));
    }

    @Test
    public void bothStopsCanBeOffTheRoute() {                                              // T2
        List<Problem> problems = booking("R200", "S6", "S7", TODAY).problems(R200, RESIDENT, TODAY);

        assertEquals(List.of("boardingStopId", "destinationStopId"), fields(problems));
    }

    @Test
    public void theDestinationMustComeAfterTheBoardingStop() {                             // T2
        assertEquals(List.of("destinationStopId"), fields(booking("R100", "S5", "S1", TODAY).problems(R100, RESIDENT, TODAY)));
        assertEquals(List.of("destinationStopId"), fields(booking("R100", "S3", "S3", TODAY).problems(R100, RESIDENT, TODAY)));
        assertEquals("The destination must come after the boarding stop",
                messageFor(booking("R100", "S5", "S1", TODAY).problems(R100, RESIDENT, TODAY), "destinationStopId"));
    }

    @Test
    public void todayAndThirtyDaysAheadAreFineButYesterdayAndThirtyOneAreNot() {           // T3
        assertTrue(booking("R100", "S1", "S5", TODAY).problems(R100, RESIDENT, TODAY).isEmpty());
        assertTrue(booking("R100", "S1", "S5", TODAY.plusDays(30)).problems(R100, RESIDENT, TODAY).isEmpty());

        List<Problem> past = booking("R100", "S1", "S5", TODAY.minusDays(1)).problems(R100, RESIDENT, TODAY);
        List<Problem> far = booking("R100", "S1", "S5", TODAY.plusDays(31)).problems(R100, RESIDENT, TODAY);

        assertEquals("Travel date must be today or later", messageFor(past, "travelDate"));
        assertEquals("Travel date can be at most 30 days ahead", messageFor(far, "travelDate"));
    }

    @Test
    public void aTemporaryAccountCannotBookADayAfterItsVisaEnds() {                        // T4
        Account tourist = new TemporaryAccount(100, 4, 4, TODAY.plusDays(10));

        assertTrue(booking("R100", "S1", "S5", TODAY.plusDays(10)).problems(R100, tourist, TODAY).isEmpty());
        List<Problem> problems = booking("R100", "S1", "S5", TODAY.plusDays(11)).problems(R100, tourist, TODAY);

        assertEquals(List.of("travelDate"), fields(problems));
        assertEquals("Your account works through 31 Oct 2026, so you cannot book a later day",
                messageFor(problems, "travelDate"));
    }

    @Test
    public void aPermanentAccountCanBookAnyDayInTheWindow() {                              // T4
        assertTrue(booking("R100", "S1", "S5", TODAY.plusDays(30)).problems(R100, RESIDENT, TODAY).isEmpty());
    }

    @Test
    public void aTouristWhoseVisaHasEndedCannotBookEvenToday() {                           // T4
        Account expired = new TemporaryAccount(100, 5, 5, TODAY.minusDays(1));

        assertEquals(List.of("travelDate"), fields(booking("R100", "S1", "S5", TODAY).problems(R100, expired, TODAY)));
    }

    @Test
    public void everyKindOfProblemTogetherIsReportedInFieldOrder() {                       // T1
        List<Problem> problems = booking("R200", "S1", "S5", TODAY.minusDays(3)).problems(R200, RESIDENT, TODAY);

        assertEquals(List.of("destinationStopId", "travelDate"), fields(problems));
    }
}
