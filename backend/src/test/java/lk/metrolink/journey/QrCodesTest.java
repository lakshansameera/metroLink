package lk.metrolink.journey;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.HashSet;
import java.util.Set;
import lk.metrolink.journey.service.QrCodes;
import org.junit.Test;

/** The text of a new ticket's QR code (rule T8). */
public class QrCodesTest {

    @Test
    public void aCodeIsMlkAndEightCharactersThatCannotBeMixedUp() {
        for (int i = 0; i < 500; i++) {
            String code = QrCodes.next();
            assertTrue(code, code.matches("MLK-[A-HJ-NP-Z2-9]{8}"));
            assertEquals(12, code.length());
        }
    }

    @Test
    public void noCodeHasAnAmbiguousCharacter() {
        for (int i = 0; i < 500; i++) {
            String random = QrCodes.next().substring(4);
            assertFalse(random, random.contains("0") || random.contains("O") || random.contains("1") || random.contains("I"));
        }
    }

    @Test
    public void aThousandCodesAreAllDifferent() {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 1_000; i++) {
            seen.add(QrCodes.next());
        }
        assertEquals(1_000, seen.size());
    }
}
