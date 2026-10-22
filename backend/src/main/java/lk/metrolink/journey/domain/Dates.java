package lk.metrolink.journey.domain;

import java.time.Instant;
import java.time.LocalDate;
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
}
