package lk.metrolink.journey;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import lk.metrolink.journey.domain.PasswordHash;
import org.junit.Test;

/** O6: a password is never kept, only a salted hash of it. */
public class PasswordHashTest {

    private static final int FAST = 1_000;

    @Test
    public void theRightPasswordMatches() {
        PasswordHash hash = PasswordHash.of("Correct horse 9", FAST);

        assertTrue(hash.matches("Correct horse 9"));
    }

    @Test
    public void aWrongPasswordDoesNotMatch() {
        PasswordHash hash = PasswordHash.of("Correct horse 9", FAST);

        assertFalse(hash.matches("Correct horse 8"));
        assertFalse(hash.matches("correct horse 9"));
        assertFalse(hash.matches(""));
    }

    @Test
    public void theEncodedFormNeverContainsThePassword() {
        PasswordHash hash = PasswordHash.of("Correct horse 9", FAST);

        assertFalse(hash.encoded().contains("Correct horse 9"));
    }

    @Test
    public void twoCustomersWithTheSamePasswordGetDifferentHashes() {
        PasswordHash first = PasswordHash.of("Same password 1", FAST);
        PasswordHash second = PasswordHash.of("Same password 1", FAST);

        assertNotEquals(first.encoded(), second.encoded());
        assertTrue(first.matches("Same password 1"));
        assertTrue(second.matches("Same password 1"));
    }

    @Test
    public void aHashReadBackFromItsEncodedFormStillMatches() {
        PasswordHash original = PasswordHash.of("Correct horse 9", FAST);

        PasswordHash readBack = PasswordHash.parse(original.encoded());

        assertTrue(readBack.matches("Correct horse 9"));
        assertFalse(readBack.matches("Wrong horse 9"));
        assertEquals(original.encoded(), readBack.encoded());
    }

    @Test
    public void theHashRemembersItsOwnNumberOfRepeats() {
        assertTrue(PasswordHash.of("Correct horse 9", FAST).encoded().startsWith(FAST + ":"));
        assertTrue(PasswordHash.of("Correct horse 9", 2_000).encoded().startsWith("2000:"));
    }

    @Test
    public void realCustomersGetTheStrongDefault() {
        assertTrue(PasswordHash.DEFAULT_ITERATIONS >= 100_000);
    }

    @Test
    public void textThatIsNotAHashIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> PasswordHash.parse("not a hash"));
    }

    @Test
    public void atLeastOneRepeatIsRequired() {
        assertThrows(IllegalArgumentException.class, () -> PasswordHash.of("Correct horse 9", 0));
    }
}
