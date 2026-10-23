package lk.metrolink.journey.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;

public final class Dates {
    public static final ZoneId SERVICE_ZONE = ZoneId.of("Asia/Colombo");

    private Dates() {

    }

    /** The Colombo calendar date of a moment in time. */
    public static LocalDate localDate(Instant instant) {
        return instant.atZone(SERVICE_ZONE).toLocalDate();
    }

    /*
        Today in Colombo. Only for wiring and the demo
        rules receive the scan time instead.
    */
    public static LocalDate today() {
        return localDate(Instant.now());
    }

    /** The current moment. Services take it as a parameter so tests can control the clock. */
    public static Instant now() {
        return Instant.now();
    }

    /** Reads an ISO-8601 moment with an offset, such as 2026-10-22T08:40:00+05:30 or 2026-10-22T03:10:00Z. */
    public static Instant parseInstant(String text) {
        return OffsetDateTime.parse(text).toInstant();
    }
}
