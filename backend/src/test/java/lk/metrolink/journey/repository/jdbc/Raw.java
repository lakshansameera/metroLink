package lk.metrolink.journey.repository.jdbc;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * A second, plain JDBC connection to the same database, so a test can run SQL the program never would and see what the
 * tables themselves allow. A statement that works is kept, so later checks can build on it; one that fails changes
 * nothing.
 */
final class Raw implements AutoCloseable {
    private final Connection connection;

    Raw(String url) throws SQLException {
        connection = DriverManager.getConnection(url, "sa", "");
        connection.setAutoCommit(false);
    }

    /** The statement must fail with this SQLState (23505 duplicate, 23513 check, 23506 or 23503 foreign key). */
    void refused(String sqlState, String sql) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate(sql);
            fail("the database accepted: " + sql);
        } catch (SQLException e) {
            assertEquals(sql, sqlState, e.getSQLState());
        }
    }

    /** The statement must work, and it is kept. */
    void accepted(String sql) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate(sql);
        }
        connection.commit();
    }

    @Override
    public void close() throws SQLException {
        connection.rollback();
        connection.close();
    }
}
