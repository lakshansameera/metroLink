package lk.metrolink.journey;

import static org.junit.Assert.assertEquals;

import java.time.Instant;
import java.time.LocalDate;
import lk.metrolink.journey.domain.Dates;
import org.junit.Test;

public class DatesTest {

    @Test
    public void aMidnightScanBelongsToTheColomboDay() {
        // Colombo is UTC+05:30, so 18:30:00 UTC is exactly midnight there.
        assertEquals(LocalDate.parse("2026-10-21"), Dates.localDate(Instant.parse("2026-10-21T18:29:59Z")));
        assertEquals(LocalDate.parse("2026-10-22"), Dates.localDate(Instant.parse("2026-10-21T18:30:00Z")));
    }

    @Test
    public void theServiceZoneIsColombo() {
        assertEquals("Asia/Colombo", Dates.SERVICE_ZONE.getId());
    }

}
