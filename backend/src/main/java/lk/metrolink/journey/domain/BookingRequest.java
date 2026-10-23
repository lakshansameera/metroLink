package lk.metrolink.journey.domain;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * What a customer asked for when booking a ticket, and the rules about it (T1 to T4).
 *
 * <p>The request knows whether it is acceptable. {@link #problems} lists everything that is wrong, so the customer
 * hears about all of it at once. It is given the facts it cannot know itself: the route (or null if there is none by
 * that number), the customer's account, and today's date.
 */
public final class BookingRequest {

    /** How far ahead a ticket can be booked (rule T3). */
    public static final int MAX_DAYS_AHEAD = 30;

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);

    /** One wrong field and what is wrong with it. */
    public static final class Problem {
        private final String field;
        private final String message;

        public Problem(String field, String message) {
            this.field = field;
            this.message = message;
        }

        public String getField() { return field; }
        public String getMessage() { return message; }
    }

    private final long passengerId;
    private final String routeNo;
    private final String boardingStopId;
    private final String destinationStopId;
    private final LocalDate travelDate;

    /** Any text or the date may be null; {@link #problems} reports what is missing. */
    public BookingRequest(long passengerId, String routeNo, String boardingStopId, String destinationStopId,
                          LocalDate travelDate) {
        this.passengerId = passengerId;
        this.routeNo = trimmed(routeNo);
        this.boardingStopId = trimmed(boardingStopId);
        this.destinationStopId = trimmed(destinationStopId);
        this.travelDate = travelDate;
    }

    private static String trimmed(String text) {
        return text == null ? null : text.trim();
    }

    private static boolean blank(String text) {
        return text == null || text.isEmpty();
    }

    /**
     * Everything wrong with this request. Empty means it can be booked.
     *
     * @param route   the route with this request's route number, or null if there is no such route
     * @param account the customer's account
     * @param today   today's date in Asia/Colombo (rule D6)
     */
    public List<Problem> problems(Route route, Account account, LocalDate today) {
        List<Problem> found = new ArrayList<>();

        if (blank(routeNo)) {
            found.add(new Problem("routeNo", "Route is required"));                                       // T1
        } else if (route == null) {
            found.add(new Problem("routeNo", "There is no route " + routeNo));                            // T2
        }

        boolean boardingOk = stopProblem(found, "boardingStopId", "Boarding stop", boardingStopId, route);
        boolean destinationOk = stopProblem(found, "destinationStopId", "Destination", destinationStopId, route);
        if (boardingOk && destinationOk && !route.isBefore(boardingStopId, destinationStopId)) {
            found.add(new Problem("destinationStopId", "The destination must come after the boarding stop")); // T2
        }

        if (travelDate == null) {
            found.add(new Problem("travelDate", "Travel date is required"));                              // T1
        } else if (travelDate.isBefore(today)) {
            found.add(new Problem("travelDate", "Travel date must be today or later"));                   // T3
        } else if (travelDate.isAfter(today.plusDays(MAX_DAYS_AHEAD))) {
            found.add(new Problem("travelDate", "Travel date can be at most " + MAX_DAYS_AHEAD + " days ahead")); // T3
        } else {
            account.lastUsableDay().filter(travelDate::isAfter).ifPresent(last -> found.add(new Problem(
                    "travelDate", "Your account works through " + DAY.format(last) + ", so you cannot book a later day"))); // T4
        }
        return found;
    }

    /** Adds a problem if the stop is missing or not on the route. True only when it is a stop of a known route. */
    private static boolean stopProblem(List<Problem> found, String field, String label, String stopId, Route route) {
        if (blank(stopId)) {
            found.add(new Problem(field, label + " is required"));
            return false;
        }
        if (route == null) {
            return false;                          // the route problem is already reported
        }
        if (!route.hasStop(stopId)) {
            found.add(new Problem(field, "Stop " + stopId + " is not on route " + route.getRouteNo()));
            return false;
        }
        return true;
    }

    public long getPassengerId() { return passengerId; }
    public String getRouteNo() { return routeNo; }
    public String getBoardingStopId() { return boardingStopId; }
    public String getDestinationStopId() { return destinationStopId; }
    public LocalDate getTravelDate() { return travelDate; }
}
