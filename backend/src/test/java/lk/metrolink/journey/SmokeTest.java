package lk.metrolink.journey;

import static org.junit.Assert.assertEquals;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.LocalDate;
import org.junit.Test;

public class SmokeTest {

    @Test
    public void toolchainWorks() throws Exception {
        ObjectMapper json = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        assertEquals("\"2026-07-01\"", json.writeValueAsString(LocalDate.of(2026, 7, 1)));
    }
}
