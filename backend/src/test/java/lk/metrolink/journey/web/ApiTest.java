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
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/** The API over real HTTP on a free port: the same requests the React screen makes. */
public class ApiTest {

    private static final LocalDate TODAY = LocalDate.parse("2026-10-21");
    private static final String MORNING = "2026-10-21T09:30:00+05:30";
    private static final String LATER = "2026-10-21T10:10:00+05:30";

    private ApiServer server;
    private HttpClient client;

    @Before
    public void setUp() throws Exception {
        server = ApiServer.start(Container.create(TODAY), 0);
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

        Reply statement = get("/api/passengers/1/account");
        assertEquals(200, statement.status);
        assertEquals(431, statement.json.get("passenger").get("balance").asInt());
        assertEquals("FARE-1", statement.json.get("charges").get(0).get("reference").asText());
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
}
