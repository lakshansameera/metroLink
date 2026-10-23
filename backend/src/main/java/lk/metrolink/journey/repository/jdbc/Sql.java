package lk.metrolink.journey.repository.jdbc;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Locale;
import lk.metrolink.journey.repository.ConstraintViolationException;
import lk.metrolink.journey.repository.DataAccessException;

/** Small helpers shared by the JDBC repositories: moments and dates, optional values, ids and error translation. */
final class Sql {

    private Sql() {
    }

    // ---- moments and dates. An Instant is stored as a UTC TIMESTAMP, so it never depends on the server's time zone.

    static void setInstant(PreparedStatement statement, int index, Instant value) throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.TIMESTAMP);
        } else {
            statement.setObject(index, LocalDateTime.ofInstant(value, ZoneOffset.UTC));
        }
    }

    static Instant getInstant(ResultSet row, String column) throws SQLException {
        LocalDateTime value = row.getObject(column, LocalDateTime.class);
        return value == null ? null : value.toInstant(ZoneOffset.UTC);
    }

    static void setDate(PreparedStatement statement, int index, LocalDate value) throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.DATE);
        } else {
            statement.setObject(index, value);
        }
    }

    static LocalDate getDate(ResultSet row, String column) throws SQLException {
        return row.getObject(column, LocalDate.class);
    }

    // ---- optional values

    static void setLong(PreparedStatement statement, int index, Long value) throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.BIGINT);
        } else {
            statement.setLong(index, value);
        }
    }

    static Long getLong(ResultSet row, String column) throws SQLException {
        long value = row.getLong(column);
        return row.wasNull() ? null : value;
    }

    static void setString(PreparedStatement statement, int index, String value) throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.VARCHAR);
        } else {
            statement.setString(index, value);
        }
    }

    // ---- emails

    /** The form of an email used to compare two: trimmed, and in lower case (rule O2). */
    static String emailKey(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    // ---- ids

    /**
     * The next free id for {@code table}. The counter is part of the same transaction as the insert that uses the
     * id, so a change that is rolled back gives its ids back, exactly as the in-memory store does.
     */
    static long nextId(Connection connection, String table) throws SQLException {
        try (PreparedStatement bump = connection.prepareStatement(
                "UPDATE id_counters SET next_value = next_value + 1 WHERE name = ?")) {
            bump.setString(1, table);
            if (bump.executeUpdate() == 1) {
                try (PreparedStatement read = connection.prepareStatement(
                        "SELECT next_value FROM id_counters WHERE name = ?")) {
                    read.setString(1, table);
                    try (ResultSet row = read.executeQuery()) {
                        row.next();
                        return row.getLong(1) - 1;
                    }
                }
            }
        }
        try (PreparedStatement first = connection.prepareStatement(
                "INSERT INTO id_counters (name, next_value) VALUES (?, 2)")) {
            first.setString(1, table);
            first.executeUpdate();
            return 1;
        }
    }

    // ---- errors

    /**
     * Turns what the database said into what the rest of the program understands. A broken rule becomes a
     * {@link ConstraintViolationException} that names the table and the rule; anything else is a
     * {@link DataAccessException}. It goes by the SQL state, which PostgreSQL and H2 report precisely (23505
     * duplicate, 23503 and 23506 foreign key, 23513 and 23514 check). A database that reports every broken rule as
     * plain 23000, as MySQL does, still gets a {@link ConstraintViolationException}, only with a less specific
     * message.
     */
    static RuntimeException refusal(SQLException e, String table, String unique, String foreignKey, String check) {
        String state = e.getSQLState() == null ? "" : e.getSQLState();
        switch (state) {
            case "23505":
                return new ConstraintViolationException(table + ": UNIQUE " + unique + " failed");
            case "23503":                          // standard: a row still points at this one
            case "23506":                          // H2: the row this one points at does not exist
                return new ConstraintViolationException(table + ": FOREIGN KEY " + foreignKey + " failed");
            case "23513":
            case "23514":
                return new ConstraintViolationException(table + ": CHECK " + check + " failed");
            default:
                return state.startsWith("23")
                        ? new ConstraintViolationException(table + ": constraint failed")
                        : failure(table, e);
        }
    }

    static DataAccessException failure(String table, SQLException e) {
        return new DataAccessException("Database error on " + table + ": " + e.getMessage(), e);
    }
}
