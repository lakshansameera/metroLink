package lk.metrolink.journey.repository.jdbc;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import lk.metrolink.journey.domain.PasswordHash;
import lk.metrolink.journey.repository.CredentialRepository;

/** Password hashes, in a table of their own. The column holds {@code iterations:salt:hash}, never a password. */
public final class JdbcCredentialRepository implements CredentialRepository {
    private final JdbcDatabase db;

    public JdbcCredentialRepository(JdbcDatabase db) {
        this.db = db;
    }

    @Override
    public void insert(long passengerId, PasswordHash hash) {
        try (PreparedStatement statement = db.connection().prepareStatement(
                "INSERT INTO credentials (passenger_id, password_hash) VALUES (?, ?)")) {
            statement.setLong(1, passengerId);
            statement.setString(2, hash.encoded());
            statement.executeUpdate();
        } catch (SQLException e) {
            throw Sql.refusal(e, "credentials", "passenger_id", "passenger_id", "password_hash");
        }
    }

    @Override
    public Optional<PasswordHash> findByPassenger(long passengerId) {
        try (PreparedStatement statement = db.connection().prepareStatement(
                "SELECT password_hash FROM credentials WHERE passenger_id = ?")) {
            statement.setLong(1, passengerId);
            try (ResultSet row = statement.executeQuery()) {
                return row.next() ? Optional.of(PasswordHash.parse(row.getString(1))) : Optional.empty();
            }
        } catch (SQLException e) {
            throw Sql.failure("credentials", e);
        }
    }
}
