package lk.metrolink.journey;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lk.metrolink.journey.domain.JourneyState;
import lk.metrolink.journey.domain.TicketState;
import lk.metrolink.journey.domain.TripComparison;
import lk.metrolink.journey.repository.OnEveryStore;
import lk.metrolink.journey.service.ReaderDisplay;
import lk.metrolink.journey.service.ScanDirection;
import lk.metrolink.journey.service.ScanRequest;
import lk.metrolink.journey.service.view.JourneyView;
import lk.metrolink.journey.service.view.TicketView;
import org.junit.Before;
import org.junit.Test;

/**
 * What the screen shows for a ticket and a journey must be what the server has (rules V1 to V4 in docs/rules.md).
 * The screen's list of states lives in frontend/src/types.ts. This test reads that file, so the two cannot drift
 * apart without a test failing, and then walks a ticket through its life to prove which pair of states the screen
 * shows at each step.
 */
public class FrontEndStatesTest extends OnEveryStore {

    private static final LocalDate TODAY = LocalDate.parse("2026-10-21");
    private static final Instant MORNING = Instant.parse("2026-10-21T04:00:00Z");
    private static final Instant LATER = Instant.parse("2026-10-21T04:40:00Z");

    private Container app;

    @Before
    public void setUp() {
        app = Container.create(open(TODAY), TODAY);
    }

    // ---- the lists agree (V1, V2)

    private static List<String> frontEndList(String constant) throws IOException {
        Path types = Optional.of(Path.of("..", "frontend", "src", "types.ts")).filter(Files::exists)
                .or(() -> Optional.of(Path.of("frontend", "src", "types.ts")).filter(Files::exists))
                .orElse(null);
        assumeTrue("the front end is not next to the backend, so there is nothing to compare", types != null);
        String source = Files.readString(types, StandardCharsets.UTF_8);
        Matcher list = Pattern.compile(constant + "\\s*=\\s*\\[([^\\]]*)\\]").matcher(source);
        assertTrue(constant + " is not in types.ts", list.find());
        List<String> names = new ArrayList<>();
        Matcher name = Pattern.compile("'([A-Z_]+)'").matcher(list.group(1));
        while (name.find()) {
            names.add(name.group(1));
        }
        return names;
    }

    @Test
    public void theScreenKnowsExactlyTheTicketStatesOfTheServer() throws IOException {                // V1
        assertEquals(TicketState.names(), frontEndList("TICKET_STATUSES"));
    }

    @Test
    public void theScreenKnowsExactlyTheJourneyStatesOfTheServer() throws IOException {               // V1
        assertEquals(JourneyState.names(), frontEndList("JOURNEY_STATUSES"));
    }

    @Test
    public void theScreenKnowsExactlyTheKindsOfTripOfTheServer() throws IOException {                 // V5
        assertEquals(TripComparison.Kind.names(), frontEndList("TRIP_KINDS"));
    }

    @Test
    public void everyKindNamedIsARealKind() {                                                         // V5
        for (String name : TripComparison.Kind.names()) {
            assertEquals(name, TripComparison.Kind.valueOf(name).name());
        }
        assertEquals(TripComparison.Kind.values().length, TripComparison.Kind.names().size());
    }

    @Test
    public void everyNamedStateIsARealStateThatKnowsItsOwnName() {                                    // V1
        for (String name : TicketState.names()) {
            assertEquals(name, TicketState.of(name).name());
        }
        for (String name : JourneyState.names()) {
            assertEquals(name, JourneyState.of(name).name());
        }
    }

    @Test
    public void theDatabaseStoresExactlyTheSameStates() throws IOException {                          // V2
        String schema;
        try (InputStream in = getClass().getResourceAsStream("/lk/metrolink/journey/repository/jdbc/schema.sql")) {
            schema = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        assertEquals(TicketState.names(), allowedBy(schema, "ck_tickets_status"));
        assertEquals(JourneyState.names(), allowedBy(schema, "ck_journeys_status"));
    }

    private static List<String> allowedBy(String schema, String constraint) {
        Matcher check = Pattern.compile(constraint + "\\s+CHECK\\s*\\(status IN \\(([^)]*)\\)\\)").matcher(schema);
        assertTrue(constraint + " is not in schema.sql", check.find());
        List<String> names = new ArrayList<>();
        Matcher name = Pattern.compile("'([A-Z_]+)'").matcher(check.group(1));
        while (name.find()) {
            names.add(name.group(1));
        }
        return names;
    }

    // ---- the pair of states the screen shows at each step (V3, V4)

    private void tap(String qr, ScanDirection direction, String stop) {
        ReaderDisplay shown = app.reader.scan(new ScanRequest(qr, direction, stop,
                direction == ScanDirection.IN ? MORNING : LATER));
        // The reader face shows the same two states as the lists do.
        if (shown.getTicket() != null) {
            assertEquals(ticketState(qr), shown.getTicket().getStatus());
        }
        if (shown.getJourney() != null) {
            assertEquals(journeyState(qr), shown.getJourney().getStatus());
        }
    }

    private String ticketState(String qr) {
        return app.queries.tickets().stream().filter(t -> t.getQrCode().equals(qr)).map(TicketView::getStatus)
                .findFirst().orElseThrow();
    }

    /** "none" while the ticket has no journey, so the screen shows only the ticket's state. */
    private String journeyState(String qr) {
        return app.queries.journeys().stream().filter(j -> j.getQrCode().equals(qr)).map(JourneyView::getStatus)
                .findFirst().orElse("none");
    }

    private void assertScreenShows(String qr, String ticket, String journey) {
        assertEquals("ticket state of " + qr, ticket, ticketState(qr));
        assertEquals("journey state of " + qr, journey, journeyState(qr));
        assertTrue(TicketState.names().contains(ticketState(qr)));
        assertTrue(journey.equals("none") || JourneyState.names().contains(journeyState(qr)));
    }

    @Test
    public void aTicketAndItsJourneyShowTheStatesTheServerMovesThemThrough() {                        // V3
        assertScreenShows("MLK-HAPPY01", "ACTIVE", "none");

        tap("MLK-HAPPY01", ScanDirection.IN, "S1");
        assertScreenShows("MLK-HAPPY01", "ACTIVE", "IN_PROGRESS");          // the ticket is not used yet

        tap("MLK-HAPPY01", ScanDirection.OUT, "S5");
        assertScreenShows("MLK-HAPPY01", "USED", "COMPLETED");              // they change together, in one unit of work
    }

    @Test
    public void aFareThatCannotBePaidLeavesTheTicketActiveUntilItIsPaid() {                           // V3
        tap("MLK-PENDING", ScanDirection.IN, "S1");
        tap("MLK-PENDING", ScanDirection.OUT, "S5");                        // owes LKR 69, has LKR 45
        assertScreenShows("MLK-PENDING", "ACTIVE", "PAYMENT_PENDING");

        app.testData.credit(2, 100, LATER);
        app.reader.settle(app.queries.journeys().stream().filter(j -> j.getQrCode().equals("MLK-PENDING"))
                .findFirst().orElseThrow().getJourneyId(), LATER);
        assertScreenShows("MLK-PENDING", "USED", "COMPLETED");
    }

    @Test
    public void aRefusedTapChangesNoState() {                                                         // V4
        tap("MLK-NOFUNDS", ScanDirection.IN, "S1");                         // too little credit
        assertScreenShows("MLK-NOFUNDS", "ACTIVE", "none");

        tap("MLK-OLD0001", ScanDirection.IN, "S1");                         // yesterday's ticket
        assertScreenShows("MLK-OLD0001", "ACTIVE", "none");                 // still ACTIVE: nothing expires it by itself

        tap("MLK-CANCEL01", ScanDirection.IN, "S1");
        assertScreenShows("MLK-CANCEL01", "CANCELLED", "none");
    }

    @Test
    public void everyStateTheServerSendsIsOneTheScreenKnows() throws IOException {                    // V1
        tap("MLK-HAPPY01", ScanDirection.IN, "S1");
        tap("MLK-HAPPY01", ScanDirection.OUT, "S5");
        tap("MLK-PENDING", ScanDirection.IN, "S1");
        tap("MLK-PENDING", ScanDirection.OUT, "S5");
        tap("MLK-OVERTR1", ScanDirection.IN, "S1");
        tap("MLK-OVERTR1", ScanDirection.OUT, "S5");                        // over-travel

        List<String> ticketsOnScreen = frontEndList("TICKET_STATUSES");
        List<String> journeysOnScreen = frontEndList("JOURNEY_STATUSES");
        app.queries.tickets().forEach(t -> assertTrue(t.getStatus(), ticketsOnScreen.contains(t.getStatus())));
        app.queries.journeys().forEach(j -> assertTrue(j.getStatus(), journeysOnScreen.contains(j.getStatus())));

        List<String> tripsOnScreen = frontEndList("TRIP_KINDS");
        app.queries.journeys().stream().map(j -> j.getTrip().getKind()).filter(kind -> kind != null)
                .forEach(kind -> assertTrue(kind, tripsOnScreen.contains(kind)));
        assertTrue("an over-travel trip was among them", app.queries.journeys().stream()
                .anyMatch(j -> "OVER_TRAVEL".equals(j.getTrip().getKind())));
    }

    @Test
    public void theTripShownForAJourneyIsTheOneTheReaderShowedAtTheTap() {                            // V5
        ReaderDisplay in = app.reader.scan(new ScanRequest("MLK-HAPPY01", ScanDirection.IN, "S2", MORNING));
        ReaderDisplay out = app.reader.scan(new ScanRequest("MLK-HAPPY01", ScanDirection.OUT, "S6", LATER));
        JourneyView listed = app.queries.journeys().stream().filter(j -> j.getQrCode().equals("MLK-HAPPY01"))
                .findFirst().orElseThrow();

        assertEquals("the face and the list say the same", out.getJourney().getTrip().getKind(),
                listed.getTrip().getKind());
        assertEquals(out.getJourney().getTrip().getFareDifference(), listed.getTrip().getFareDifference());
        assertEquals("OVER_TRAVEL", listed.getTrip().getKind());
        assertTrue(in.getJourney().getTrip().isBoardedOffTicket());
        assertTrue(listed.isOvertravelled());
    }
}
