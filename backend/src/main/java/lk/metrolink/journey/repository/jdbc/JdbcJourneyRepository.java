package lk.metrolink.journey.repository.jdbc;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lk.metrolink.journey.domain.Journey;
import lk.metrolink.journey.domain.JourneyState;
import lk.metrolink.journey.repository.ConstraintViolationException;
import lk.metrolink.journey.repository.JourneyRepository;

public final class JdbcJourneyRepository implements JourneyRepository {
    private static final String SELECT =
            "SELECT j.journey_id, j.ticket_id, j.route_no, j.boarding_stop_id, j.tap_in_time, j.exit_stop_id, "
                    + "j.tap_out_time, j.fare_due, j.fare_charged, j.status FROM journeys j";

    private final JdbcDatabase db;

    public JdbcJourneyRepository(JdbcDatabase db) {
        this.db = db;
    }

    @Override
    public Optional<Journey> findById(long journeyId) {
        try (PreparedStatement statement = db.connection().prepareStatement(SELECT + " WHERE j.journey_id = ?")) {
            statement.setLong(1, journeyId);
            return one(statement);
        } catch (SQLException e) {
            throw Sql.failure("journeys", e);
        }
    }

    @Override
    public Optional<Journey> findByTicketId(long ticketId) {
        try (PreparedStatement statement = db.connection().prepareStatement(SELECT + " WHERE j.ticket_id = ?")) {
            statement.setLong(1, ticketId);
            return one(statement);
        } catch (SQLException e) {
            throw Sql.failure("journeys", e);
        }
    }

    /** Newest first. */
    @Override
    public List<Journey> list() {
        try (PreparedStatement statement = db.connection().prepareStatement(SELECT + " ORDER BY j.journey_id DESC")) {
            return many(statement);
        } catch (SQLException e) {
            throw Sql.failure("journeys", e);
        }
    }

    /** The journeys on this passenger's tickets, newest first. */
    @Override
    public List<Journey> listForPassenger(long passengerId) {
        try (PreparedStatement statement = db.connection().prepareStatement(
                SELECT + " JOIN tickets t ON t.ticket_id = j.ticket_id WHERE t.passenger_id = ? "
                        + "ORDER BY j.journey_id DESC")) {
            statement.setLong(1, passengerId);
            return many(statement);
        } catch (SQLException e) {
            throw Sql.failure("journeys", e);
        }
    }

    @Override
    public Journey insert(Journey draft) {
        try {
            long id = Sql.nextId(db.connection(), "journeys");
            try (PreparedStatement statement = db.connection().prepareStatement(
                    "INSERT INTO journeys (journey_id, ticket_id, route_no, boarding_stop_id, tap_in_time, "
                            + "exit_stop_id, tap_out_time, fare_due, fare_charged, status) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
                statement.setLong(1, id);
                bind(statement, 2, draft);
                statement.executeUpdate();
            }
            return draft.withId(id);
        } catch (SQLException e) {
            throw refusal(e);
        }
    }

    @Override
    public void save(Journey journey) {
        try (PreparedStatement statement = db.connection().prepareStatement(
                "UPDATE journeys SET ticket_id = ?, route_no = ?, boarding_stop_id = ?, tap_in_time = ?, "
                        + "exit_stop_id = ?, tap_out_time = ?, fare_due = ?, fare_charged = ?, status = ? "
                        + "WHERE journey_id = ?")) {
            bind(statement, 1, journey);
            statement.setLong(10, journey.getJourneyId());
            if (statement.executeUpdate() == 0) {
                throw new ConstraintViolationException("journeys: no journey " + journey.getJourneyId());
            }
        } catch (SQLException e) {
            throw refusal(e);
        }
    }

    /** Binds the nine columns after the id, starting at {@code first}. */
    private static void bind(PreparedStatement statement, int first, Journey journey) throws SQLException {
        statement.setLong(first, journey.getTicketId());
        statement.setString(first + 1, journey.getRouteNo());
        statement.setString(first + 2, journey.getBoardingStopId());
        Sql.setInstant(statement, first + 3, journey.getTapInTime());
        Sql.setString(statement, first + 4, journey.getExitStopId());
        Sql.setInstant(statement, first + 5, journey.getTapOutTime());
        Sql.setLong(statement, first + 6, journey.getFareDue());
        Sql.setLong(statement, first + 7, journey.getFareCharged());
        statement.setString(first + 8, journey.status());
    }

    private static RuntimeException refusal(SQLException e) {
        return Sql.refusal(e, "journeys", "ticket_id", "ticket_id, route_no or stop", "fares >= 0");
    }

    private static Optional<Journey> one(PreparedStatement statement) throws SQLException {
        List<Journey> found = many(statement);
        return found.isEmpty() ? Optional.empty() : Optional.of(found.get(0));
    }

    private static List<Journey> many(PreparedStatement statement) throws SQLException {
        try (ResultSet rows = statement.executeQuery()) {
            List<Journey> found = new ArrayList<>();
            while (rows.next()) {
                found.add(new Journey(rows.getLong("journey_id"), rows.getLong("ticket_id"),
                        rows.getString("route_no"), rows.getString("boarding_stop_id"),
                        Sql.getInstant(rows, "tap_in_time"), rows.getString("exit_stop_id"),
                        Sql.getInstant(rows, "tap_out_time"), Sql.getLong(rows, "fare_due"),
                        Sql.getLong(rows, "fare_charged"), JourneyState.of(rows.getString("status"))));
            }
            return found;
        }
    }
}
