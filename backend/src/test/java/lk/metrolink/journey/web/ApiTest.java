package lk.metrolink.journey.web;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import lk.metrolink.journey.Container;
import lk.metrolink.journey.domain.Dates;
import lk.metrolink.journey.repository.OnEveryStore;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/** The API over real HTTP on a free port: the same requests the React screen makes, on every store. */
public class ApiTest extends OnEveryStore {

    private static final LocalDate TODAY = LocalDate.parse("2026-10-21");
    private static final String MORNING = "2026-10-21T09:30:00+05:30";
    private static final String LATER = "2026-10-21T10:10:00+05:30";

    private ApiServer server;
    private HttpClient client;

    @Before
    public void setUp() throws Exception {
        server = ApiServer.start(Container.create(open(TODAY), TODAY), 0);
        client = HttpClient.newHttpClient();
    }

    @After
    public void tearDown() {
        server.stop();
    }

    private static final class Reply {
        final int status;
        final JsonNode json;

        Reply(int status, JsonNode json) {
            this.status = status;
            this.json = json;
        }
    }

    private Reply send(String method, String path, String body) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://localhost:" + server.port() + path));
        if (body == null) {
            builder.method(method, HttpRequest.BodyPublishers.noBody());
        } else {
            builder.header("Content-Type", "application/json").method(method, HttpRequest.BodyPublishers.ofString(body));
        }
        HttpResponse<String> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        JsonNode json = response.body().isEmpty() ? null : Json.MAPPER.readTree(response.body());
        return new Reply(response.statusCode(), json);
    }

    private Reply get(String path) throws Exception {
        return send("GET", path, null);
    }

    private Reply post(String path, String body) throws Exception {
        return send("POST", path, body);
    }

    private Reply scan(String qr, String direction, String stop, String at) throws Exception {
        return post("/api/reader/scan", "{\"qrCode\":\"" + qr + "\",\"direction\":\"" + direction
                + "\",\"stopId\":\"" + stop + "\",\"scannedAt\":\"" + at + "\"}");
    }

    @Test
    public void healthSaysOk() throws Exception {
        Reply reply = get("/api/health");

        assertEquals(200, reply.status);
        assertEquals("ok", reply.json.get("status").asText());
    }

    @Test
    public void referenceListsTheNetwork() throws Exception {
        Reply reply = get("/api/reference");

        assertEquals(200, reply.status);
        assertEquals(7, reply.json.get("stops").size());
        assertEquals("R100", reply.json.get("routes").get(0).get("routeNo").asText());
        assertEquals(7, reply.json.get("routes").get(0).get("stops").size());
        assertEquals("Colombo Fort", reply.json.get("routes").get(0).get("stops").get(0).get("name").asText());
    }

    @Test
    public void aFullJourneyOverHttp() throws Exception {
        Reply boarded = scan("MLK-HAPPY01", "IN", "S1", MORNING);
        assertEquals(200, boarded.status);
        assertEquals("JOURNEY_STARTED", boarded.json.get("outcome").asText());
        assertEquals("SUCCESS", boarded.json.get("tone").asText());
        assertEquals("IN_PROGRESS", boarded.json.get("journey").get("status").asText());
        assertEquals(69, boarded.json.get("fare").get("required").asInt());

        Reply exited = scan("MLK-HAPPY01", "OUT", "S5", LATER);
        assertEquals(200, exited.status);
        assertEquals("JOURNEY_COMPLETED", exited.json.get("outcome").asText());
        assertEquals("Fare LKR 69 charged. Balance LKR 431.", exited.json.get("message").asText());
        assertEquals(69, exited.json.get("fare").get("charged").asInt());
        assertEquals("COMPLETED", exited.json.get("journey").get("status").asText());
        assertEquals("2026-10-21T04:00:00Z", exited.json.get("journey").get("tapInTime").asText());
        assertFalse(exited.json.get("overtravelled").asBoolean());
        assertEquals("AS_TICKETED", exited.json.get("journey").get("trip").get("kind").asText());
        assertEquals(69, exited.json.get("journey").get("trip").get("quotedFare").asInt());
        assertEquals(0, exited.json.get("journey").get("trip").get("fareDifference").asInt());
        assertFalse(exited.json.get("journey").get("trip").get("overcharged").asBoolean());

        Reply statement = get("/api/passengers/1/account");
        assertEquals(200, statement.status);
        assertEquals(431, statement.json.get("passenger").get("balance").asInt());
        assertEquals("FARE-1", statement.json.get("charges").get(0).get("reference").asText());
    }

    @Test
    public void aTripOffTheTicketIsOverTravelAndOverChargedOverHttp() throws Exception {
        Reply boarded = scan("MLK-HAPPY01", "IN", "S2", MORNING);                              // the ticket says S1 to S5
        assertEquals("JOURNEY_STARTED", boarded.json.get("outcome").asText());
        assertEquals("WARNING", boarded.json.get("tone").asText());
        assertTrue(boarded.json.get("journey").get("trip").get("boardedOffTicket").asBoolean());
        assertTrue("not decided before the exit", boarded.json.get("journey").get("trip").get("kind").isNull());

        Reply exited = scan("MLK-HAPPY01", "OUT", "S6", LATER);
        JsonNode trip = exited.json.get("journey").get("trip");
        assertEquals("JOURNEY_COMPLETED", exited.json.get("outcome").asText());
        assertTrue(exited.json.get("overtravelled").asBoolean());
        assertTrue(exited.json.get("journey").get("overtravelled").asBoolean());
        assertEquals("OVER_TRAVEL", trip.get("kind").asText());
        assertTrue(trip.get("boardedOffTicket").asBoolean());
        assertTrue(trip.get("exitedOffTicket").asBoolean());
        assertEquals(69, trip.get("quotedFare").asInt());
        assertEquals(7, trip.get("fareDifference").asInt());
        assertTrue(trip.get("overcharged").asBoolean());
        assertEquals(76, exited.json.get("fare").get("charged").asInt());
    }

    @Test
    public void aRefusedTicketIsStillAnAnswerNotAnError() throws Exception {
        Reply reply = scan("NOT-A-TICKET", "IN", "S1", MORNING);

        assertEquals(200, reply.status);
        assertEquals("REJECTED", reply.json.get("outcome").asText());
        assertEquals("ERROR", reply.json.get("tone").asText());
        assertEquals("UNKNOWN_TOKEN", reply.json.get("reason").asText());
        assertFalse("absent values are left out", reply.json.has("journey"));
    }

    @Test
    public void anUnpaidFareIsAWarningAndLaterSettlingCompletesIt() throws Exception {
        scan("MLK-PENDING", "IN", "S1", MORNING);
        Reply waiting = scan("MLK-PENDING", "OUT", "S5", LATER);
        assertEquals(200, waiting.status);
        assertEquals("PAYMENT_PENDING", waiting.json.get("outcome").asText());
        assertEquals("WARNING", waiting.json.get("tone").asText());
        assertEquals(24, waiting.json.get("fare").get("shortfall").asInt());
        long journeyId = waiting.json.get("journey").get("journeyId").asLong();

        Reply credited = post("/api/test-data/credit", "{\"passengerId\":2,\"amount\":100}");
        assertEquals(200, credited.status);
        assertEquals(145, credited.json.get("balance").asInt());

        Reply settled = post("/api/journeys/" + journeyId + "/settle", null);
        assertEquals(200, settled.status);
        assertEquals("JOURNEY_COMPLETED", settled.json.get("outcome").asText());
        assertEquals(76, settled.json.get("fare").get("balance").asInt());
    }

    @Test
    public void settlingAJourneyThatHasNotEndedIsAConflict() throws Exception {
        Reply boarded = scan("MLK-HAPPY01", "IN", "S1", MORNING);
        long journeyId = boarded.json.get("journey").get("journeyId").asLong();

        Reply reply = post("/api/journeys/" + journeyId + "/settle", null);

        assertEquals(409, reply.status);
        assertEquals("ILLEGAL_TRANSITION", reply.json.get("error").asText());
    }

    @Test
    public void unknownThingsAre404() throws Exception {
        assertEquals(404, post("/api/journeys/99/settle", null).status);
        assertEquals(404, get("/api/journeys/99").status);
        assertEquals(404, get("/api/passengers/99/account").status);
        assertEquals(404, scan("MLK-HAPPY01", "IN", "NOWHERE", MORNING).status);
        assertEquals(404, get("/api/nothing-here").status);
        assertEquals(404, get("/elsewhere").status);
        assertEquals("NOT_FOUND", get("/api/journeys/99").json.get("error").asText());
    }

    @Test
    public void aBadRequestListsEveryWrongField() throws Exception {
        Reply reply = post("/api/reader/scan", "{\"direction\":\"SIDEWAYS\",\"scannedAt\":\"yesterday\"}");

        assertEquals(400, reply.status);
        assertEquals("VALIDATION", reply.json.get("error").asText());
        List<String> paths = new ArrayList<>();
        reply.json.get("details").forEach(d -> paths.add(d.get("path").asText()));
        assertTrue(paths.toString(), paths.containsAll(List.of("qrCode", "direction", "stopId", "scannedAt")));
    }

    @Test
    public void aBodyThatIsNotJsonIs400() throws Exception {
        Reply reply = post("/api/reader/scan", "{not json");

        assertEquals(400, reply.status);
        assertEquals("BAD_JSON", reply.json.get("error").asText());
    }

    @Test
    public void anIdThatIsNotANumberIs400() throws Exception {
        assertEquals(400, get("/api/journeys/abc").status);
        assertEquals(400, post("/api/journeys/0/settle", null).status);
    }

    @Test
    public void creditMustBeAPositiveWholeAmount() throws Exception {
        assertEquals(400, post("/api/test-data/credit", "{\"passengerId\":1,\"amount\":-5}").status);
        assertEquals(400, post("/api/test-data/credit", "{\"passengerId\":1,\"amount\":1.5}").status);
        assertEquals(400, post("/api/test-data/credit", "{\"passengerId\":1}").status);
        assertEquals(404, post("/api/test-data/credit", "{\"passengerId\":99,\"amount\":10}").status);
    }

    @Test
    public void anExpiredAccountCannotBeToppedUp() throws Exception {
        Reply reply = post("/api/test-data/credit", "{\"passengerId\":5,\"amount\":10}");

        assertEquals(422, reply.status);
        assertEquals("ACCOUNT_EXPIRED", reply.json.get("error").asText());
    }

    @Test
    public void resetBringsBackTheDemoData() throws Exception {
        scan("MLK-HAPPY01", "IN", "S1", MORNING);
        scan("MLK-HAPPY01", "OUT", "S5", LATER);

        assertEquals(200, post("/api/test-data/reset", null).status);

        assertEquals(0, get("/api/journeys").json.size());
        assertEquals(500, get("/api/passengers").json.get(0).get("balance").asInt());
    }

    @Test
    public void listsNameEverything() throws Exception {
        scan("MLK-HAPPY01", "IN", "S1", MORNING);

        JsonNode passengers = get("/api/passengers").json;
        JsonNode tickets = get("/api/tickets").json;
        JsonNode journeys = get("/api/journeys").json;

        assertEquals(5, passengers.size());
        assertEquals("TEMPORARY", passengers.get(3).get("accountType").asText());
        assertEquals("2026-10-31", passengers.get(3).get("visaExpiry").asText());
        assertEquals(10, tickets.size());
        assertEquals("Colombo Fort", tickets.get(0).get("boardingStopName").asText());
        assertEquals(1, journeys.size());
        assertEquals("Nimal Perera", journeys.get(0).get("passengerName").asText());
    }

    @Test
    public void sixExitTapsAtTheSameMomentChargeOnce() throws Exception {
        scan("MLK-HAPPY01", "IN", "S1", MORNING);

        ExecutorService pool = Executors.newFixedThreadPool(6);
        List<Future<Reply>> replies = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            replies.add(pool.submit(() -> scan("MLK-HAPPY01", "OUT", "S5", LATER)));
        }
        int completed = 0;
        int alreadySettled = 0;
        for (Future<Reply> future : replies) {
            String outcome = future.get().json.get("outcome").asText();
            if (outcome.equals("JOURNEY_COMPLETED")) completed++;
            if (outcome.equals("ALREADY_SETTLED")) alreadySettled++;
        }
        pool.shutdown();

        assertEquals(1, completed);
        assertEquals(5, alreadySettled);
        assertEquals(431, get("/api/passengers").json.get(0).get("balance").asInt());
        assertEquals(1, get("/api/passengers/1/account").json.get("charges").size());
    }

    @Test
    public void theBrowserMayCallFromAnotherPort() throws Exception {
        HttpRequest preflight = HttpRequest.newBuilder(URI.create("http://localhost:" + server.port() + "/api/reader/scan"))
                .method("OPTIONS", HttpRequest.BodyPublishers.noBody()).build();

        HttpResponse<String> response = client.send(preflight, HttpResponse.BodyHandlers.ofString());

        assertEquals(204, response.statusCode());
        assertEquals("*", response.headers().firstValue("Access-Control-Allow-Origin").orElse(""));
    }

    @Test
    public void thePermissionCheckAsksTheCallerToUseANewConnectionForItsNextRequest() throws Exception {
        // The JDK server sometimes dropped a connection that was reused at once after a 204 answer, so the caller
        // that sent this one is asked not to reuse it. Without this, two checks in a row failed now and then.
        HttpRequest preflight = HttpRequest.newBuilder(URI.create("http://localhost:" + server.port() + "/api/reader/scan"))
                .method("OPTIONS", HttpRequest.BodyPublishers.noBody()).build();

        HttpResponse<String> response = client.send(preflight, HttpResponse.BodyHandlers.ofString());

        assertEquals("close", response.headers().firstValue("Connection").orElse(""));
    }

    // ---- customer onboarding: register and log in (O1 to O9)

    private static final String PASSWORD = "Sunny day 42";

    /** The service checks a visa date against the real clock, so these tests ask the same clock. */
    private static String visaInDays(int days) {
        return Dates.today().plusDays(days).toString();
    }

    private static String registration(String name, String email, String password, String residency, String visa) {
        StringBuilder body = new StringBuilder("{");
        body.append("\"name\":\"").append(name).append("\",");
        body.append("\"email\":\"").append(email).append("\",");
        body.append("\"password\":\"").append(password).append("\",");
        body.append("\"residency\":\"").append(residency).append("\"");
        if (visa != null) {
            body.append(",\"visaExpiry\":\"").append(visa).append("\"");
        }
        return body.append("}").toString();
    }

    private Reply register(String email) throws Exception {
        return post("/api/onboarding/register", registration("Amaya Jayasuriya", email, PASSWORD, "RESIDENT", null));
    }

    private Reply login(String email, String password) throws Exception {
        return post("/api/onboarding/login", "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}");
    }

    private static List<String> paths(Reply reply) {
        List<String> found = new ArrayList<>();
        reply.json.get("details").forEach(d -> found.add(d.get("path").asText()));
        return found;
    }

    @Test
    public void registeringAResidentOpensAPermanentAccountWithNoCredit() throws Exception {
        Reply reply = register("amaya@example.com");

        assertEquals(201, reply.status);
        assertEquals(6, reply.json.get("userId").asInt());
        assertEquals("Amaya Jayasuriya", reply.json.get("customer").get("name").asText());
        assertEquals("PERMANENT", reply.json.get("customer").get("accountType").asText());
        assertEquals(0, reply.json.get("customer").get("balance").asInt());
        assertTrue(reply.json.get("message").asText().startsWith("Welcome, Amaya Jayasuriya."));
    }

    @Test
    public void registeringANonResidentOpensATemporaryAccountWithTheVisaDate() throws Exception {
        String visa = visaInDays(30);

        Reply reply = post("/api/onboarding/register",
                registration("Emma Walker", "emma.w@example.com", PASSWORD, "NON_RESIDENT", visa));

        assertEquals(201, reply.status);
        assertEquals("TEMPORARY", reply.json.get("customer").get("accountType").asText());
        assertEquals(visa, reply.json.get("customer").get("visaExpiry").asText());
    }

    @Test
    public void aRegisteredCustomerIsInThePassengerListAndCanBeCredited() throws Exception {
        register("amaya@example.com");

        JsonNode list = get("/api/passengers").json;
        assertEquals(6, list.size());
        assertEquals("amaya@example.com", list.get(5).get("email").asText());

        Reply credited = post("/api/test-data/credit", "{\"passengerId\":6,\"amount\":100}");
        assertEquals(200, credited.status);
        assertEquals(100, credited.json.get("balance").asInt());
    }

    @Test
    public void noResponseEverContainsThePasswordOrItsHash() throws Exception {
        Reply registered = register("amaya@example.com");
        Reply loggedIn = login("amaya@example.com", PASSWORD);
        Reply list = get("/api/passengers");

        for (Reply reply : new Reply[] {registered, loggedIn, list}) {
            String text = reply.json.toString().toLowerCase();
            assertFalse(text, text.contains(PASSWORD.toLowerCase()));
            assertFalse(text, text.contains("hash"));
            assertFalse(text, text.contains("salt"));
            assertFalse(text, text.contains("password"));
        }
    }

    @Test
    public void everyWrongFieldIsListedInOneAnswer() throws Exception {
        Reply reply = post("/api/onboarding/register", registration("", "nope", "short", "RESIDENT", null));

        assertEquals(400, reply.status);
        assertEquals("VALIDATION", reply.json.get("error").asText());
        assertEquals(List.of("name", "email", "password"), paths(reply));
        assertEquals(5, get("/api/passengers").json.size());
    }

    @Test
    public void anEmptyBodyListsWhatIsMissing() throws Exception {
        Reply reply = post("/api/onboarding/register", "{}");

        assertEquals(400, reply.status);
        assertEquals(List.of("name", "email", "password", "residency"), paths(reply));
    }

    @Test
    public void fieldsOfTheWrongTypeAreRefused() throws Exception {
        Reply reply = post("/api/onboarding/register",
                "{\"name\":5,\"email\":\"a@b.co\",\"password\":\"Sunny day 42\",\"residency\":\"RESIDENT\"}");

        assertEquals(400, reply.status);
        assertEquals(List.of("name"), paths(reply));
    }

    @Test
    public void anUnknownResidencyIsRefused() throws Exception {
        Reply reply = post("/api/onboarding/register",
                registration("Amaya", "amaya@example.com", PASSWORD, "TOURIST", null));

        assertEquals(400, reply.status);
        assertEquals(List.of("residency"), paths(reply));
    }

    @Test
    public void aVisaDateThatIsNotADateIsRefused() throws Exception {
        Reply reply = post("/api/onboarding/register",
                registration("Emma", "emma.w@example.com", PASSWORD, "NON_RESIDENT", "31/12/2030"));

        assertEquals(400, reply.status);
        assertEquals(List.of("visaExpiry"), paths(reply));
    }

    @Test
    public void aNonResidentNeedsAVisaDateThatIsNotInThePast() throws Exception {
        Reply missing = post("/api/onboarding/register",
                registration("Emma", "emma.w@example.com", PASSWORD, "NON_RESIDENT", null));
        Reply past = post("/api/onboarding/register",
                registration("Emma", "emma.w@example.com", PASSWORD, "NON_RESIDENT", visaInDays(-1)));

        assertEquals(400, missing.status);
        assertEquals(List.of("visaExpiry"), paths(missing));
        assertEquals(400, past.status);
        assertEquals(List.of("visaExpiry"), paths(past));
    }

    @Test
    public void aSecondRegistrationWithTheSameEmailIsRefused() throws Exception {
        assertEquals(201, register("amaya@example.com").status);

        Reply again = register("AMAYA@example.com");

        assertEquals(409, again.status);
        assertEquals("EMAIL_TAKEN", again.json.get("error").asText());
        assertEquals(6, get("/api/passengers").json.size());
    }

    @Test
    public void sixPeopleRegisteringTheSameEmailAtOnceOnlyOneSucceeds() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(6);
        List<Future<Reply>> replies = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            replies.add(pool.submit(() -> register("race@example.com")));
        }
        int created = 0;
        int refused = 0;
        for (Future<Reply> future : replies) {
            int status = future.get().status;
            if (status == 201) created++;
            if (status == 409) refused++;
        }
        pool.shutdown();

        assertEquals(1, created);
        assertEquals(5, refused);
        assertEquals(6, get("/api/passengers").json.size());
    }

    @Test
    public void aRegisteredCustomerCanLogIn() throws Exception {
        int userId = register("amaya@example.com").json.get("userId").asInt();

        Reply reply = login("amaya@example.com", PASSWORD);

        assertEquals(200, reply.status);
        assertEquals(userId, reply.json.get("userId").asInt());
        assertEquals("Welcome back, Amaya Jayasuriya.", reply.json.get("message").asText());
    }

    @Test
    public void aDemoCustomerCanLogInWithTheDemoPassword() throws Exception {
        Reply reply = login("nimal@example.com", "Demo@1234");

        assertEquals(200, reply.status);
        assertEquals(1, reply.json.get("userId").asInt());
        assertEquals("Nimal Perera", reply.json.get("customer").get("name").asText());
    }

    @Test
    public void aWrongPasswordAndAnUnknownEmailGetTheSameAnswer() throws Exception {
        register("amaya@example.com");

        Reply wrongPassword = login("amaya@example.com", "Rainy day 42");
        Reply unknownEmail = login("nobody@example.com", PASSWORD);

        assertEquals(401, wrongPassword.status);
        assertEquals(401, unknownEmail.status);
        assertEquals("INVALID_LOGIN", wrongPassword.json.get("error").asText());
        assertEquals(wrongPassword.json.toString(), unknownEmail.json.toString());
    }

    @Test
    public void aLoginWithMissingFieldsIsAMalformedRequest() throws Exception {
        Reply reply = post("/api/onboarding/login", "{}");

        assertEquals(400, reply.status);
        assertEquals(List.of("email", "password"), paths(reply));
    }

    @Test
    public void resettingTheDemoDataForgetsRegisteredCustomers() throws Exception {
        register("amaya@example.com");

        post("/api/test-data/reset", null);

        assertEquals(5, get("/api/passengers").json.size());
        assertEquals(401, login("amaya@example.com", PASSWORD).status);
        assertEquals(6, register("amaya@example.com").json.get("userId").asInt());
    }

    // ---- booking a ticket (T1 to T12)

    private static String booking(Object passengerId, String route, String from, String to, String date) {
        StringBuilder body = new StringBuilder("{");
        if (passengerId != null) {
            body.append("\"passengerId\":").append(passengerId).append(",");
        }
        if (route != null) {
            body.append("\"routeNo\":\"").append(route).append("\",");
        }
        if (from != null) {
            body.append("\"boardingStopId\":\"").append(from).append("\",");
        }
        if (to != null) {
            body.append("\"destinationStopId\":\"").append(to).append("\",");
        }
        if (date != null) {
            body.append("\"travelDate\":\"").append(date).append("\",");
        }
        if (body.charAt(body.length() - 1) == ',') {
            body.setLength(body.length() - 1);
        }
        return body.append("}").toString();
    }

    /** Booking checks the date against the real clock, as registration does, so these tests ask the same clock. */
    private static String inDays(int days) {
        return Dates.today().plusDays(days).toString();
    }

    @Test
    public void bookingATicketAnswers201WithTheNewTicketAndTheFare() throws Exception {
        Reply reply = post("/api/tickets", booking(1, "R100", "S1", "S5", inDays(0)));

        assertEquals(201, reply.status);
        JsonNode ticket = reply.json.get("ticket");
        assertEquals(11, ticket.get("ticketId").asInt());
        assertEquals(1, ticket.get("passengerId").asInt());
        assertEquals("R100", ticket.get("routeNo").asText());
        assertEquals("S1", ticket.get("boardingStopId").asText());
        assertEquals("S5", ticket.get("plannedDestinationStopId").asText());
        assertEquals(inDays(0), ticket.get("travelDate").asText());
        assertEquals(69, ticket.get("quotedFare").asInt());
        assertEquals("ACTIVE", ticket.get("status").asText());
        assertTrue(ticket.get("qrCode").asText().matches("MLK-[A-HJ-NP-Z2-9]{8}"));
        assertEquals(500, reply.json.get("balance").asInt());
        assertTrue(reply.json.get("canPayNow").asBoolean());
        assertTrue(reply.json.get("message").asText().contains("LKR 69"));
    }

    @Test
    public void aBookedTicketAppearsInTheTicketListAndCanBeScanned() throws Exception {
        String qr = post("/api/tickets", booking(1, "R100", "S1", "S5", inDays(0)))
                .json.get("ticket").get("qrCode").asText();

        Reply listed = get("/api/tickets");
        boolean found = false;
        for (JsonNode t : listed.json) {
            found |= t.get("qrCode").asText().equals(qr);
        }
        assertTrue(found);

        String now = java.time.Instant.now().toString();
        Reply in = scan(qr, "IN", "S1", now);
        assertEquals("JOURNEY_STARTED", in.json.get("outcome").asText());
        Reply out = scan(qr, "OUT", "S5", java.time.Instant.now().plusSeconds(1800).toString());
        assertEquals("JOURNEY_COMPLETED", out.json.get("outcome").asText());
        assertEquals("the quoted fare is the fare charged", 69, out.json.get("fare").get("charged").asInt());
        assertEquals(431, get("/api/passengers/1/account").json.get("passenger").get("balance").asInt());
    }

    @Test
    public void bookingTakesNoMoney() throws Exception {
        post("/api/tickets", booking(1, "R100", "S1", "S5", inDays(0)));

        Reply statement = get("/api/passengers/1/account");
        assertEquals(500, statement.json.get("passenger").get("balance").asInt());
        assertEquals(0, statement.json.get("charges").size());
    }

    @Test
    public void aQuoteAnswers200WithTheFareAndCreatesNoTicket() throws Exception {
        Reply reply = post("/api/tickets/quote", booking(1, "R100", "S1", "S5", inDays(0)));

        assertEquals(200, reply.status);
        assertEquals(69, reply.json.get("fare").asInt());
        assertEquals("DISTANCE", reply.json.get("fareType").asText());
        assertEquals(10.8, reply.json.get("distanceKm").asDouble(), 0.0001);
        assertEquals(500, reply.json.get("balance").asInt());
        assertTrue(reply.json.get("canPayNow").asBoolean());
        assertEquals(10, get("/api/tickets").json.size());
    }

    @Test
    public void aFlatRouteQuoteSaysFlat() throws Exception {
        Reply reply = post("/api/tickets/quote", booking(1, "R200", "S1", "S3", inDays(1)));

        assertEquals(30, reply.json.get("fare").asInt());
        assertEquals("FLAT", reply.json.get("fareType").asText());
    }

    @Test
    public void aBookingWithEverythingMissingNamesEveryField() throws Exception {
        Reply reply = post("/api/tickets", booking(1, null, null, null, null));

        assertEquals(400, reply.status);
        assertEquals("VALIDATION", reply.json.get("error").asText());
        assertEquals(List.of("routeNo", "boardingStopId", "destinationStopId", "travelDate"), paths(reply));
        assertEquals(10, get("/api/tickets").json.size());
    }

    @Test
    public void aBookingWithWrongStopsAndDateNamesBoth() throws Exception {
        Reply reply = post("/api/tickets", booking(1, "R200", "S1", "S5", inDays(-1)));

        assertEquals(400, reply.status);
        assertEquals("VALIDATION", reply.json.get("error").asText());
        assertEquals(List.of("destinationStopId", "travelDate"), paths(reply));
    }

    @Test
    public void aQuoteIsRefusedTheSameWay() throws Exception {
        Reply reply = post("/api/tickets/quote", booking(1, "R999", "S1", "S5", inDays(31)));

        assertEquals(400, reply.status);
        assertEquals(List.of("routeNo", "travelDate"), paths(reply));
    }

    @Test
    public void aBookingForACustomerWhoDoesNotExistIs404() throws Exception {
        Reply reply = post("/api/tickets", booking(99, "R100", "S1", "S5", inDays(0)));

        assertEquals(404, reply.status);
        assertEquals("NOT_FOUND", reply.json.get("error").asText());
    }

    @Test
    public void aBookingWithoutAValidPassengerIdIsAMalformedRequest() throws Exception {
        Reply missing = post("/api/tickets", booking(null, "R100", "S1", "S5", inDays(0)));
        Reply text = post("/api/tickets", booking("\"one\"", "R100", "S1", "S5", inDays(0)));
        Reply zero = post("/api/tickets", booking(0, "R100", "S1", "S5", inDays(0)));

        for (Reply reply : new Reply[] {missing, text, zero}) {
            assertEquals(400, reply.status);
            assertEquals(List.of("passengerId"), paths(reply));
        }
    }

    @Test
    public void aTravelDateThatIsNotADateIsAMalformedRequest() throws Exception {
        Reply reply = post("/api/tickets", booking(1, "R100", "S1", "S5", "next friday"));

        assertEquals(400, reply.status);
        assertEquals(List.of("travelDate"), paths(reply));
    }

    @Test
    public void aCustomerWithoutEnoughCreditStillGetsTheTicketAndIsToldToAddCredit() throws Exception {
        Reply reply = post("/api/tickets", booking(3, "R100", "S1", "S3", inDays(0)));

        assertEquals(201, reply.status);
        assertFalse(reply.json.get("canPayNow").asBoolean());
        assertEquals(10, reply.json.get("balance").asInt());
        assertTrue(reply.json.get("message").asText().contains("add credit"));
    }

    @Test
    public void aNewlyRegisteredCustomerCanBookAndSeesOnlyTheirOwnTicket() throws Exception {
        int userId = register("amaya@example.com").json.get("userId").asInt();

        Reply booked = post("/api/tickets", booking(userId, "R100", "S1", "S3", inDays(2)));

        assertEquals(201, booked.status);
        assertEquals(userId, booked.json.get("ticket").get("passengerId").asInt());
        assertEquals("Amaya Jayasuriya", booked.json.get("ticket").get("passengerName").asText());
        assertFalse("no credit yet", booked.json.get("canPayNow").asBoolean());
        int ticketId = booked.json.get("ticket").get("ticketId").asInt();
        long theirs = 0;
        long others = 0;
        for (JsonNode t : get("/api/tickets").json) {
            if (t.get("passengerId").asInt() == userId) {
                theirs++;
                assertEquals(ticketId, t.get("ticketId").asInt());
            } else if (t.get("ticketId").asInt() == ticketId) {
                others++;
            }
        }
        assertEquals(1, theirs);
        assertEquals(0, others);
    }

    @Test
    public void aTouristCannotBookPastTheVisaOverHttp() throws Exception {
        // The demo tourists' visas are dated from the demo date, not the real clock, so register a tourist the real
        // clock knows about.
        Reply tourist = post("/api/onboarding/register",
                registration("Lena Fischer", "lena.fischer@example.com", PASSWORD, "NON_RESIDENT", visaInDays(5)));
        int userId = tourist.json.get("userId").asInt();

        assertEquals(201, post("/api/tickets", booking(userId, "R100", "S1", "S3", inDays(5))).status);
        Reply late = post("/api/tickets", booking(userId, "R100", "S1", "S3", inDays(6)));

        assertEquals(400, late.status);
        assertEquals(List.of("travelDate"), paths(late));
    }
}
