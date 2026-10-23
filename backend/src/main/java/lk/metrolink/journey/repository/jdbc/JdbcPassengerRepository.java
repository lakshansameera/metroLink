package lk.metrolink.journey.repository.jdbc;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lk.metrolink.journey.domain.NonResident;
import lk.metrolink.journey.domain.Passenger;
import lk.metrolink.journey.domain.Residency;
import lk.metrolink.journey.domain.Resident;
import lk.metrolink.journey.repository.PassengerRepository;

public final class JdbcPassengerRepository implements PassengerRepository {
    private static final String COLUMNS = "SELECT passenger_id, name, email, residency, visa_expiry FROM passengers";

    private final JdbcDatabase db;

    public JdbcPassengerRepository(JdbcDatabase db) {
        this.db = db;
    }

    @Override
    public Optional<Passenger> find(long passengerId) {
        try (PreparedStatement statement = db.connection().prepareStatement(COLUMNS + " WHERE passenger_id = ?")) {
            statement.setLong(1, passengerId);
            return one(statement);
        } catch (SQLException e) {
            throw Sql.failure("passengers", e);
        }
    }

    @Override
    public Optional<Passenger> findByEmail(String email) {
        try (PreparedStatement statement = db.connection().prepareStatement(COLUMNS + " WHERE email_key = ?")) {
            statement.setString(1, Sql.emailKey(email));
            return one(statement);
        } catch (SQLException e) {
            throw Sql.failure("passengers", e);
        }
    }

    @Override
    public List<Passenger> list() {
        try (PreparedStatement statement = db.connection().prepareStatement(COLUMNS + " ORDER BY passenger_id");
             ResultSet rows = statement.executeQuery()) {
            List<Passenger> found = new ArrayList<>();
            while (rows.next()) {
                found.add(passenger(rows));
            }
            return found;
        } catch (SQLException e) {
            throw Sql.failure("passengers", e);
        }
    }

    @Override
    public Passenger insert(Passenger draft) {
        Residency residency = draft.getResidency();
        LocalDate visaExpiry = residency instanceof NonResident nonResident ? nonResident.getVisaExpiry() : null;
        try {
            long id = Sql.nextId(db.connection(), "passengers");
            try (PreparedStatement statement = db.connection().prepareStatement(
                    "INSERT INTO passengers (passenger_id, name, email, email_key, residency, visa_expiry) "
                            + "VALUES (?, ?, ?, ?, ?, ?)")) {
                statement.setLong(1, id);
                statement.setString(2, draft.getName());
                statement.setString(3, draft.getEmail());
                statement.setString(4, Sql.emailKey(draft.getEmail()));
                statement.setString(5, residency.name());
                Sql.setDate(statement, 6, visaExpiry);
                statement.executeUpdate();
            }
            return draft.withId(id);
        } catch (SQLException e) {
            throw Sql.refusal(e, "passengers", "email", "-", "residency and visa_expiry");
        }
    }

    private static Optional<Passenger> one(PreparedStatement statement) throws SQLException {
        try (ResultSet rows = statement.executeQuery()) {
            return rows.next() ? Optional.of(passenger(rows)) : Optional.empty();
        }
    }

    /** A resident is a {@link Resident}; a non-resident is a {@link NonResident} who remembers the visa date. */
    private static Passenger passenger(ResultSet row) throws SQLException {
        Residency residency = "RESIDENT".equals(row.getString("residency"))
                ? new Resident()
                : new NonResident(Sql.getDate(row, "visa_expiry"));
        return new Passenger(row.getLong("passenger_id"), row.getString("name"), row.getString("email"), residency);
    }
}
