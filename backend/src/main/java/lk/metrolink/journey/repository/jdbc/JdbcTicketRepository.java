package lk.metrolink.journey.repository.jdbc;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lk.metrolink.journey.domain.QRTicket;
import lk.metrolink.journey.domain.TicketState;
import lk.metrolink.journey.repository.ConstraintViolationException;
import lk.metrolink.journey.repository.TicketRepository;

public final class JdbcTicketRepository implements TicketRepository {
    private static final String SELECT =
            "SELECT ticket_id, qr_code, passenger_id, route_no, boarding_stop_id, planned_destination_stop_id, "
                    + "travel_date, quoted_fare, issued_at, status FROM tickets";

    private final JdbcDatabase db;

    public JdbcTicketRepository(JdbcDatabase db) {
        this.db = db;
    }

    @Override
    public Optional<QRTicket> findById(long ticketId) {
        try (PreparedStatement statement = db.connection().prepareStatement(SELECT + " WHERE ticket_id = ?")) {
            statement.setLong(1, ticketId);
            return one(statement);
        } catch (SQLException e) {
            throw Sql.failure("tickets", e);
        }
    }

    @Override
    public Optional<QRTicket> findByQrCode(String qrCode) {
        try (PreparedStatement statement = db.connection().prepareStatement(SELECT + " WHERE qr_code = ?")) {
            statement.setString(1, qrCode);
            return one(statement);
        } catch (SQLException e) {
            throw Sql.failure("tickets", e);
        }
    }

    @Override
    public List<QRTicket> list() {
        try (PreparedStatement statement = db.connection().prepareStatement(SELECT + " ORDER BY ticket_id")) {
            return many(statement);
        } catch (SQLException e) {
            throw Sql.failure("tickets", e);
        }
    }

    @Override
    public List<QRTicket> listForPassenger(long passengerId) {
        try (PreparedStatement statement = db.connection().prepareStatement(
                SELECT + " WHERE passenger_id = ? ORDER BY ticket_id")) {
            statement.setLong(1, passengerId);
            return many(statement);
        } catch (SQLException e) {
            throw Sql.failure("tickets", e);
        }
    }

    @Override
    public QRTicket insert(QRTicket draft) {
        try {
            long id = Sql.nextId(db.connection(), "tickets");
            try (PreparedStatement statement = db.connection().prepareStatement(
                    "INSERT INTO tickets (ticket_id, qr_code, passenger_id, route_no, boarding_stop_id, "
                            + "planned_destination_stop_id, travel_date, quoted_fare, issued_at, status) "
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
    public void save(QRTicket ticket) {
        try (PreparedStatement statement = db.connection().prepareStatement(
                "UPDATE tickets SET qr_code = ?, passenger_id = ?, route_no = ?, boarding_stop_id = ?, "
                        + "planned_destination_stop_id = ?, travel_date = ?, quoted_fare = ?, issued_at = ?, "
                        + "status = ? WHERE ticket_id = ?")) {
            bind(statement, 1, ticket);
            statement.setLong(10, ticket.getTicketId());
            if (statement.executeUpdate() == 0) {
                throw new ConstraintViolationException("tickets: no ticket " + ticket.getTicketId());
            }
        } catch (SQLException e) {
            throw refusal(e);
        }
    }

    /** Binds the nine columns after the id, starting at {@code first}. */
    private static void bind(PreparedStatement statement, int first, QRTicket ticket) throws SQLException {
        statement.setString(first, ticket.getQrCode());
        statement.setLong(first + 1, ticket.getPassengerId());
        statement.setString(first + 2, ticket.getRouteNo());
        statement.setString(first + 3, ticket.getBoardingStopId());
        statement.setString(first + 4, ticket.getPlannedDestinationStopId());
        Sql.setDate(statement, first + 5, ticket.getTravelDate());
        statement.setLong(first + 6, ticket.getQuotedFare());
        Sql.setInstant(statement, first + 7, ticket.getIssuedAt());
        statement.setString(first + 8, ticket.status());
    }

    private static RuntimeException refusal(SQLException e) {
        return Sql.refusal(e, "tickets", "qr_code", "passenger_id, route_no or stop", "quoted_fare >= 0");
    }

    private static Optional<QRTicket> one(PreparedStatement statement) throws SQLException {
        List<QRTicket> found = many(statement);
        return found.isEmpty() ? Optional.empty() : Optional.of(found.get(0));
    }

    private static List<QRTicket> many(PreparedStatement statement) throws SQLException {
        try (ResultSet rows = statement.executeQuery()) {
            List<QRTicket> found = new ArrayList<>();
            while (rows.next()) {
                found.add(new QRTicket(rows.getLong("ticket_id"), rows.getString("qr_code"),
                        rows.getLong("passenger_id"), rows.getString("route_no"), rows.getString("boarding_stop_id"),
                        rows.getString("planned_destination_stop_id"), Sql.getDate(rows, "travel_date"),
                        rows.getLong("quoted_fare"), Sql.getInstant(rows, "issued_at"),
                        TicketState.of(rows.getString("status"))));
            }
            return found;
        }
    }
}
