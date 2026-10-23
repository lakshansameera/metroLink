package lk.metrolink.journey.web;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Supplier;
import lk.metrolink.journey.Container;
import lk.metrolink.journey.domain.Dates;
import lk.metrolink.journey.log.Log;
import lk.metrolink.journey.log.LogCapture;
import lk.metrolink.journey.repository.Store;
import lk.metrolink.journey.repository.UnitOfWork;
import lk.metrolink.journey.repository.memory.InMemoryStore;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * The console as the front door writes it (rules L2, L3, L5 and L8): one number for each request that appears on every
 * line it causes, reads and quotes only at debug, the status of every answer, and a failure inside the program as a
 * warning while the caller still gets a clean 500.
 */
public class WebLogTest {

    private static final LocalDate TODAY = LocalDate.parse("2026-10-21");
    private static final String MORNING = "2026-10-21T09:30:00+05:30";

    private ApiServer server;
    private final HttpClient client = HttpClient.newHttpClient();

    @Before
    public void setUp() throws Exception {
        server = ApiServer.start(Container.create(TODAY), 0);
    }

    @After
    public void tearDown() {
        server.stop();
        Log.reset();
    }

    private int send(String method, String path, String body) throws Exception {
        return sendTo(server, method, path, body).statusCode();
    }

    private HttpResponse<String> sendTo(ApiServer target, String method, String path, String body) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://localhost:" + target.port() + path));
        if (body == null) {
            builder.method(method, HttpRequest.BodyPublishers.noBody());
        } else {
            builder.header("Content-Type", "application/json").method(method, HttpRequest.BodyPublishers.ofString(body));
        }
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private int scan(String qr, String direction, String stop) throws Exception {
        return send("POST", "/api/reader/scan", "{\"qrCode\":\"" + qr + "\",\"direction\":\"" + direction
                + "\",\"stopId\":\"" + stop + "\",\"scannedAt\":\"" + MORNING + "\"}");
    }

    /** The request column of a log line: {@code #12} or {@code -}. */
    private static String requestOf(String line) {
        return line.substring(19, 24).trim();
    }

    // ---- what a request shows (L5)

    @Test
    public void aTapShowsItsPathItsWorkAndItsStatusUnderOneRequestNumber() throws Exception {
        try (LogCapture capture = new LogCapture(Log.Level.INFO)) {
            assertEquals(200, scan("MLK-HAPPY01", "IN", "S1"));

            List<String> lines = capture.lines();
            assertTrue(capture.text(), lines.size() > 10);
            assertTrue(lines.get(0), lines.get(0).endsWith("WEB      POST /api/reader/scan"));
            assertTrue(lines.get(lines.size() - 1), lines.get(lines.size() - 1).contains("WEB      -> 200 in "));
            for (String line : lines) {
                assertEquals(line, "#1", requestOf(line));
            }
            capture.assertInOrder("WEB      POST /api/reader/scan", "ReaderService.scan (the Facade)",
                    "JOURNEY_STARTED", "WEB      -> 200 in ");
        }
    }

    @Test
    public void everyNewRequestGetsTheNextNumber() throws Exception {
        try (LogCapture capture = new LogCapture(Log.Level.INFO)) {
            scan("MLK-HAPPY01", "IN", "S1");
            scan("MLK-HAPPY01", "OUT", "S5");

            List<String> numbers = new ArrayList<>();
            for (String line : capture.lines(Log.WEB)) {
                if (line.contains("POST ")) {
                    numbers.add(requestOf(line));
                }
            }
            assertEquals(List.of("#1", "#2"), numbers);
        }
    }

    @Test
    public void aReadIsInvisibleAtInfoAndShownAtDebug() throws Exception {                       // L5
        try (LogCapture capture = new LogCapture(Log.Level.INFO)) {
            assertEquals(200, send("GET", "/api/health", null));
            assertEquals(200, send("GET", "/api/passengers", null));
            assertEquals(200, send("GET", "/api/reference", null));

            assertEquals("the screen reloading its lists must not bury the taps", "", capture.text());
        }
        try (LogCapture capture = new LogCapture(Log.Level.DEBUG)) {
            assertEquals(200, send("GET", "/api/health", null));

            capture.assertInOrder("WEB      GET /api/health", "WEB      -> 200 in ");
        }
    }

    @Test
    public void aFareQuoteIsInvisibleAtInfoAndShownAtDebug() throws Exception {                  // L5
        String quote = "{\"passengerId\":1,\"routeNo\":\"R100\",\"boardingStopId\":\"S1\","
                + "\"destinationStopId\":\"S5\",\"travelDate\":\"" + Dates.today() + "\"}";
        try (LogCapture capture = new LogCapture(Log.Level.INFO)) {
            assertEquals(200, send("POST", "/api/tickets/quote", quote));

            assertEquals(capture.text(), "", capture.text());
        }
        try (LogCapture capture = new LogCapture(Log.Level.DEBUG)) {
            assertEquals(200, send("POST", "/api/tickets/quote", quote));

            capture.assertInOrder("WEB      POST /api/tickets/quote", "BookingService.quote", "WEB      -> 200 in ");
        }
    }

    @Test
    public void theBrowsersPermissionCheckIsRoutineToo() throws Exception {                     // L5
        try (LogCapture capture = new LogCapture(Log.Level.INFO)) {
            assertEquals(204, send("OPTIONS", "/api/reader/scan", null));

            assertEquals(capture.text(), "", capture.text());
        }
        try (LogCapture capture = new LogCapture(Log.Level.DEBUG)) {
            assertEquals(204, send("OPTIONS", "/api/reader/scan", null));

            capture.assertInOrder("WEB      OPTIONS /api/reader/scan", "WEB      -> 204 in ");
        }
    }

    // ---- what an answer says (L3)

    @Test
    public void aRefusedRequestShowsItsStatusAndItsErrorCode() throws Exception {
        try (LogCapture capture = new LogCapture(Log.Level.INFO)) {
            assertEquals(404, send("POST", "/api/nowhere", "{}"));
            assertEquals(400, send("POST", "/api/onboarding/register", "{}"));
            assertEquals(401, send("POST", "/api/onboarding/login",
                    "{\"email\":\"nobody@example.com\",\"password\":\"whatever it is\"}"));
            String register = "{\"name\":\"Nimal\",\"email\":\"nimal.new@example.com\","
                    + "\"password\":\"a long secret password\",\"residency\":\"RESIDENT\"}";
            assertEquals(201, send("POST", "/api/onboarding/register", register));
            assertEquals(409, send("POST", "/api/onboarding/register", register));

            List<String> web = capture.lines(Log.WEB);
            assertTrue(capture.text(), web.stream().anyMatch(l -> l.contains("-> 404 NOT_FOUND in ")));
            assertTrue(capture.text(), web.stream().anyMatch(l -> l.contains("-> 400 VALIDATION in ")));
            assertTrue(capture.text(), web.stream().anyMatch(l -> l.contains("-> 401 INVALID_LOGIN in ")));
            assertTrue(capture.text(), web.stream().anyMatch(l -> l.contains("-> 201 in ")));
            assertTrue(capture.text(), web.stream().anyMatch(l -> l.contains("-> 409 EMAIL_TAKEN in ")));
            assertFalse(capture.text(), capture.text().contains("nimal.new"));
            assertFalse(capture.text(), capture.text().contains("a long secret password"));
        }
    }

    @Test
    public void aFailedLoginShowsOneWarningAndTheOtherLinesOfTheSameRequestShareItsNumber() throws Exception {
        try (LogCapture capture = new LogCapture(Log.Level.WARN)) {
            assertEquals(401, send("POST", "/api/onboarding/login",
                    "{\"email\":\"nobody@example.com\",\"password\":\"whatever it is\"}"));

            assertEquals(capture.text(), 1, capture.lines().size());
            assertEquals("#1", requestOf(capture.lines().get(0)));
            assertTrue(capture.text(), capture.text().contains("WARN  #1    SERVICE  OnboardingService.login: refused"));
            assertFalse(capture.text(), capture.text().contains("nobody"));
            assertFalse(capture.text(), capture.text().contains("whatever it is"));
        }
    }

    // ---- requests that overlap (L3)

    @Test
    public void lineOfRequestsThatRunTogetherCanStillBeToldApartByTheirNumber() throws Exception {
        ExecutorService callers = Executors.newFixedThreadPool(8);
        try (LogCapture capture = new LogCapture(Log.Level.INFO)) {
            List<Future<Integer>> answers = new ArrayList<>();
            for (int i = 0; i < 24; i++) {
                String qr = i % 2 == 0 ? "MLK-OLD0001" : "MLK-NOFUNDS";
                answers.add(callers.submit(() -> scan(qr, "IN", "S1")));
            }
            for (Future<Integer> answer : answers) {
                assertEquals(200, answer.get().intValue());
            }

            Map<String, List<String>> byRequest = new LinkedHashMap<>();
            for (String line : capture.lines()) {
                byRequest.computeIfAbsent(requestOf(line), k -> new ArrayList<>()).add(line);
            }
            assertEquals(capture.text(), 24, byRequest.size());
            for (Map.Entry<String, List<String>> request : byRequest.entrySet()) {
                List<String> lines = request.getValue();
                assertTrue(request.getKey() + " " + lines, request.getKey().startsWith("#"));
                assertTrue(lines.get(0), lines.get(0).contains("WEB      POST /api/reader/scan"));
                assertTrue(lines.get(lines.size() - 1), lines.get(lines.size() - 1).contains("WEB      -> 200 in "));
                assertEquals("one begin and one commit for each request", 1,
                        lines.stream().filter(l -> l.contains("UOW      begin:")).count());
                assertEquals(1, lines.stream().filter(l -> l.contains("UOW      commit:")).count());
            }
        } finally {
            callers.shutdownNow();
        }
    }

    @Test
    public void linesWrittenOutsideAnyRequestShowADash() {
        try (LogCapture capture = new LogCapture(Log.Level.INFO)) {
            Container.create(TODAY);

            assertTrue(capture.text(), capture.lines().size() > 0);
            for (String line : capture.lines()) {
                assertEquals(line, "-", requestOf(line));
            }
        }
    }

    // ---- when something breaks (L8)

    @Test
    public void anUnexpectedFailureIsAWarningWithTheReasonAndTheCallerStillGetsACleanFiveHundred() throws Exception {
        ApiServer broken = ApiServer.start(Container.create(brokenStore(), TODAY), 0);
        PrintStream realErr = System.err;
        ByteArrayOutputStream swallowed = new ByteArrayOutputStream();
        System.setErr(new PrintStream(swallowed));            // the stack trace is expected here, so keep it out of the build
        try (LogCapture capture = new LogCapture(Log.Level.WARN)) {
            HttpResponse<String> answer = sendTo(broken, "POST", "/api/reader/scan",
                    "{\"qrCode\":\"MLK-HAPPY01\",\"direction\":\"IN\",\"stopId\":\"S1\",\"scannedAt\":\"" + MORNING + "\"}");

            assertEquals(500, answer.statusCode());
            assertTrue(answer.body(), answer.body().contains("INTERNAL"));
            assertFalse("the reason is for the console, not for the caller", answer.body().contains("on fire"));
            assertEquals(capture.text(), 2, capture.lines().size());
            capture.assertInOrder("WARN  #1    WEB      unexpected error, answering 500: IllegalStateException: the disk is on fire",
                    "WARN  #1    WEB      -> 500 INTERNAL in ");
        } finally {
            System.setErr(realErr);
            broken.stop();
        }
        assertTrue("the stack trace still goes to the error output", swallowed.toString().contains("the disk is on fire"));
    }

    @Test
    public void aFailedUnitOfWorkIsShownAsARollbackWithTheSameReason() throws Exception {
        ApiServer broken = ApiServer.start(Container.create(brokenStore(), TODAY), 0);
        PrintStream realErr = System.err;
        System.setErr(new PrintStream(new ByteArrayOutputStream()));
        try (LogCapture capture = new LogCapture(Log.Level.INFO)) {
            sendTo(broken, "POST", "/api/reader/scan",
                    "{\"qrCode\":\"MLK-HAPPY01\",\"direction\":\"IN\",\"stopId\":\"S1\",\"scannedAt\":\"" + MORNING + "\"}");

            capture.assertInOrder("WEB      POST /api/reader/scan", "UOW      begin:",
                    "UOW      rollback: IllegalStateException: the disk is on fire", "WEB      unexpected error",
                    "WEB      -> 500 INTERNAL");
        } finally {
            System.setErr(realErr);
            broken.stop();
        }
    }

    /** A store whose unit of work always fails, so the program breaks in a way no rule predicted. */
    private static Store brokenStore() {
        Store real = InMemoryStore.seeded(TODAY);
        UnitOfWork onFire = new UnitOfWork() {
            @Override
            public <T> T run(Supplier<T> work) {
                throw new IllegalStateException("the disk is on fire");
            }
        };
        return (Store) Proxy.newProxyInstance(Store.class.getClassLoader(), new Class<?>[] {Store.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("unitOfWork")) {
                        return onFire;
                    }
                    try {
                        return method.invoke(real, args);
                    } catch (InvocationTargetException e) {
                        throw e.getCause();
                    }
                });
    }
}
