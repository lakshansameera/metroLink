package lk.metrolink.journey.repository.jdbc;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.sql.SQLException;
import lk.metrolink.journey.repository.ConstraintViolationException;
import lk.metrolink.journey.repository.DataAccessException;
import org.junit.Test;

/** How what a database says becomes what the program understands, from the SQL state alone. */
public class SqlTest {

    private static RuntimeException refusal(String sqlState) {
        return Sql.refusal(new SQLException("the database said no", sqlState), "things", "the_unique", "the_foreign", "the_check");
    }

    private static void assertViolation(String sqlState, String expectedMessage) {
        RuntimeException e = refusal(sqlState);
        assertTrue(sqlState + " -> " + e, e instanceof ConstraintViolationException);
        assertEquals(sqlState, expectedMessage, e.getMessage());
        assertEquals(409, ((ConstraintViolationException) e).status());
    }

    @Test
    public void aDuplicateNamesTheUniqueRule() {
        assertViolation("23505", "things: UNIQUE the_unique failed");
    }

    @Test
    public void aMissingOrStillUsedRowNamesTheForeignKey() {
        assertViolation("23503", "things: FOREIGN KEY the_foreign failed");   // standard: a row still points at this one
        assertViolation("23506", "things: FOREIGN KEY the_foreign failed");   // H2: the row pointed at does not exist
    }

    @Test
    public void aBrokenCheckNamesTheCheck() {
        assertViolation("23513", "things: CHECK the_check failed");
        assertViolation("23514", "things: CHECK the_check failed");
    }

    @Test
    public void anyOtherIntegrityProblemIsStillAConstraintViolation() {
        assertViolation("23000", "things: constraint failed");                // what MySQL says for all of them
    }

    @Test
    public void anythingElseIsADataAccessFailureThatKeepsTheCause() {
        SQLException cause = new SQLException("connection lost", "08006");

        RuntimeException e = Sql.refusal(cause, "things", "u", "f", "c");

        assertTrue(e instanceof DataAccessException);
        assertTrue(e.getMessage(), e.getMessage().contains("things") && e.getMessage().contains("connection lost"));
        assertEquals(cause, e.getCause());
    }

    @Test
    public void aMissingSqlStateIsAFailureNotAViolation() {
        assertTrue(Sql.refusal(new SQLException("odd"), "things", "u", "f", "c") instanceof DataAccessException);
    }

    @Test
    public void anEmailIsComparedWithoutCaseAndWithoutSurroundingSpaces() {
        assertEquals("nimal@example.com", Sql.emailKey("  Nimal@Example.COM "));
    }
}
