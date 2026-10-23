package lk.metrolink.journey.web;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import lk.metrolink.journey.Container;
import lk.metrolink.journey.domain.BookingRequest;
import lk.metrolink.journey.domain.OnboardingRequest;
import lk.metrolink.journey.domain.ResidencyKind;
import lk.metrolink.journey.service.ScanDirection;
import lk.metrolink.journey.service.ScanRequest;

/**
 * Every endpoint of the API, in one place.
 *
 * <p>What the reader decides is always an answer, never an error: a refused ticket or an unpaid fare is HTTP 200 with
 * an outcome, a tone and a sentence, because the reader must beep either way. Only a malformed request is 400, an
 * unknown id or stop is 404, and an action that is not allowed in the current state is 409.
 */
final class Api {

    private Api() {
    }

    static Router routes(Container c) {
        Router router = new Router();

        router.add("GET", "/health", request -> ApiResponse.ok(Map.of("status", "ok")));

        // Read-only: what the screens show.
        router.add("GET", "/reference", request -> ApiResponse.ok(c.queries.reference()));
        router.add("GET", "/passengers", request -> ApiResponse.ok(c.queries.passengers()));
        router.add("GET", "/passengers/{id}/account", request -> ApiResponse.ok(c.queries.statement(request.id("id"))));
        router.add("GET", "/tickets", request -> ApiResponse.ok(c.queries.tickets()));
        router.add("GET", "/journeys", request -> ApiResponse.ok(c.queries.journeys()));
        router.add("GET", "/journeys/{id}", request -> ApiResponse.ok(c.queries.journey(request.id("id"))));

        // Journey Execution: the reader forwards a token and a stop.
        router.add("POST", "/reader/scan", request -> {
            Fields fields = Fields.of(request.body());
            String qrCode = fields.text("qrCode");
            ScanDirection direction = fields.enumValue("direction", ScanDirection.class);
            String stopId = fields.text("stopId");
            Instant scannedAt = fields.optionalInstant("scannedAt");
            fields.check();
            return ApiResponse.ok(c.reader.scan(new ScanRequest(qrCode, direction, stopId, scannedAt)));
        });

        // Customer onboarding: the "New user - Registration" and "Existing user - Login" branches of the Top Up
        // Credit sequence. A registration that cannot go ahead is an error here (400, 409), unlike a refused ticket,
        // because the customer has to change something and try again.
        router.add("POST", "/onboarding/register", request -> {
            Fields fields = Fields.of(request.body());
            String name = fields.optionalText("name");
            String email = fields.optionalText("email");
            String password = fields.optionalText("password");
            ResidencyKind residency = fields.optionalEnum("residency", ResidencyKind.class);
            LocalDate visaExpiry = fields.optionalDate("visaExpiry");
            fields.check();
            return ApiResponse.created(
                    c.onboarding.register(new OnboardingRequest(name, email, password, residency, visaExpiry)));
        });
        router.add("POST", "/onboarding/login", request -> {
            Fields fields = Fields.of(request.body());
            String email = fields.text("email");
            String password = fields.secret("password");
            fields.check();
            return ApiResponse.ok(c.onboarding.login(email, password));
        });

        // Ticket booking: ask what a trip costs, then book it. Both check the same rules, and a request that cannot go
        // ahead is an error here (400, 404), because the customer has to change something and try again. Booking is
        // 201 Created. Neither moves any money: the fare is charged at the exit.
        router.add("POST", "/tickets/quote", request -> ApiResponse.ok(c.booking.quote(bookingRequest(request))));
        router.add("POST", "/tickets", request -> ApiResponse.created(c.booking.book(bookingRequest(request))));

        // After a top-up, pay the fare of a journey that is waiting for payment.
        router.add("POST", "/journeys/{id}/settle", request -> ApiResponse.ok(c.reader.settle(request.id("id"))));

        // Test data: stands in for Top Up Credit, which is a different function.
        router.add("POST", "/test-data/credit", request -> {
            Fields fields = Fields.of(request.body());
            long passengerId = fields.positiveInt("passengerId", Long.MAX_VALUE);
            long amount = fields.positiveInt("amount", 100_000);
            fields.check();
            return ApiResponse.ok(c.testData.credit(passengerId, amount));
        });
        router.add("POST", "/test-data/reset", request -> {
            c.testData.reset();
            return ApiResponse.ok(Map.of("status", "reset"));
        });

        return router;
    }

    private static BookingRequest bookingRequest(ApiRequest request) {
        Fields fields = Fields.of(request.body());
        long passengerId = fields.positiveInt("passengerId", Long.MAX_VALUE);
        String routeNo = fields.optionalText("routeNo");
        String boardingStopId = fields.optionalText("boardingStopId");
        String destinationStopId = fields.optionalText("destinationStopId");
        LocalDate travelDate = fields.optionalDate("travelDate");
        fields.check();
        return new BookingRequest(passengerId, routeNo, boardingStopId, destinationStopId, travelDate);
    }
}
