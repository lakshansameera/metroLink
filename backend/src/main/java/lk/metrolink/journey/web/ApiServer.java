package lk.metrolink.journey.web;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import lk.metrolink.journey.Container;
import lk.metrolink.journey.domain.DomainException;
import lk.metrolink.journey.domain.BookingRequest;
import lk.metrolink.journey.domain.InvalidBookingException;
import lk.metrolink.journey.domain.InvalidRegistrationException;
import lk.metrolink.journey.domain.OnboardingRequest;
import lk.metrolink.journey.log.Log;

/**
 * The HTTP front door, on the JDK's built-in server, so no framework is needed. Requests run on a pool of threads, so
 * two taps at the same moment really do run together. The unit of work lets only one of them change data at a time.
 *
 * <p>This class turns every kind of failure into the right status and an {@link ErrorBody}. The services only throw
 * exceptions; they know nothing about HTTP (exception translation).
 */
public final class ApiServer {

    private static final String API_PREFIX = "/api";

    private final HttpServer server;
    private final ExecutorService executor;
    private final Router router;
    private final AtomicLong requestNumber = new AtomicLong();

    private ApiServer(HttpServer server, ExecutorService executor, Router router) {
        this.server = server;
        this.executor = executor;
        this.router = router;
    }

    /** Starts listening. Pass port 0 to let the operating system choose a free port, then ask {@link #port()}. */
    public static ApiServer start(Container container, int port) throws IOException {
        HttpServer http = HttpServer.create(new InetSocketAddress(port), 0);
        AtomicInteger threadNumber = new AtomicInteger();
        ExecutorService pool = Executors.newFixedThreadPool(16, runnable -> {
            Thread thread = new Thread(runnable, "api-" + threadNumber.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        });
        ApiServer api = new ApiServer(http, pool, Api.routes(container));
        http.createContext("/", api::handle);
        http.setExecutor(pool);
        http.start();
        return api;
    }

    public int port() {
        return server.getAddress().getPort();
    }

    public void stop() {
        server.stop(0);
        executor.shutdownNow();
    }

    private void handle(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        String path = exchange.getRequestURI().getPath();
        // Every console line written while this request runs carries its number.
        Log.beginRequest(requestNumber.incrementAndGet(), isRoutine(method, path));
        long started = System.nanoTime();
        try (exchange) {
            Log.info(Log.WEB, "%s %s", method, path);
            // The React screen runs on another port while developing, so allow it to call us.
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET,POST,OPTIONS");
            exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");

            if (method.equals("OPTIONS")) {
                finished(204, null, started);
                exchange.sendResponseHeaders(204, -1);
                return;
            }
            ApiResponse response = respond(exchange, method);
            // Logged before the answer is sent, so by the time a caller has the answer its log lines are all written.
            finished(response.status(), response.body() instanceof ErrorBody ? ((ErrorBody) response.body()).getError() : null,
                    started);
            send(exchange, response);
        } finally {
            Log.endRequest();
        }
    }

    /**
     * A request the screen makes all the time without the customer changing anything: every read, the browser's
     * permission check, and the fare quote shown while stops are being chosen. Its INFO lines show only at DEBUG.
     */
    private static boolean isRoutine(String method, String path) {
        return !method.equals("POST") || path.endsWith("/quote");
    }

    private static void finished(int status, String error, long started) {
        long millis = (System.nanoTime() - started) / 1_000_000;
        String what = error == null ? "" : " " + error;
        if (status >= 500) {
            Log.warn(Log.WEB, "-> %d%s in %d ms", status, what, millis);
        } else {
            Log.info(Log.WEB, "-> %d%s in %d ms", status, what, millis);
        }
    }

    private ApiResponse respond(HttpExchange exchange, String method) {
        try {
            String path = exchange.getRequestURI().getPath();
            if (!path.equals(API_PREFIX) && !path.startsWith(API_PREFIX + "/")) {
                return unknownEndpoint();
            }
            byte[] body = exchange.getRequestBody().readAllBytes();
            ApiRequest request = new ApiRequest(method, path.substring(API_PREFIX.length()), Map.of(), body);
            Optional<ApiResponse> response = router.dispatch(request);
            return response.orElseGet(ApiServer::unknownEndpoint);
        } catch (ValidationException e) {
            return new ApiResponse(400, new ErrorBody("VALIDATION", "Invalid request", e.issues()));
        } catch (BadJsonException e) {
            return new ApiResponse(400, new ErrorBody("BAD_JSON", e.getMessage(), null));
        } catch (InvalidRegistrationException e) {
            return new ApiResponse(400, new ErrorBody("VALIDATION", "Invalid request", issuesOf(e)));
        } catch (InvalidBookingException e) {
            return new ApiResponse(400, new ErrorBody("VALIDATION", "Invalid request", issuesOf(e)));
        } catch (DomainException e) {
            return new ApiResponse(e.status(), new ErrorBody(e.code(), e.getMessage(), null));
        } catch (Exception e) {
            Log.warn(Log.WEB, "unexpected error, answering 500: %s: %s", e.getClass().getSimpleName(), e.getMessage());
            e.printStackTrace();
            return new ApiResponse(500, new ErrorBody("INTERNAL", "Unexpected server error", null));
        }
    }

    /** The registration problems, in the same shape as every other 400: one entry per wrong field. */
    private static List<ValidationException.Issue> issuesOf(InvalidRegistrationException e) {
        List<ValidationException.Issue> issues = new ArrayList<>();
        for (OnboardingRequest.Problem problem : e.problems()) {
            issues.add(new ValidationException.Issue(problem.getField(), problem.getMessage()));
        }
        return issues;
    }

    /** The booking problems, in the same shape as every other 400. */
    private static List<ValidationException.Issue> issuesOf(InvalidBookingException e) {
        List<ValidationException.Issue> issues = new ArrayList<>();
        for (BookingRequest.Problem problem : e.problems()) {
            issues.add(new ValidationException.Issue(problem.getField(), problem.getMessage()));
        }
        return issues;
    }

    private static ApiResponse unknownEndpoint() {
        return new ApiResponse(404, new ErrorBody("NOT_FOUND", "Unknown endpoint", null));
    }

    private static void send(HttpExchange exchange, ApiResponse response) throws IOException {
        byte[] json = Json.MAPPER.writeValueAsBytes(response.body());
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(response.status(), json.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(json);
        }
    }
}
