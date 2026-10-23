package lk.metrolink.journey.repository.jdbc;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lk.metrolink.journey.domain.FareTransaction;
import lk.metrolink.journey.repository.FareTransactionRepository;

public final class JdbcFareTransactionRepository implements FareTransactionRepository {
    private static final String SELECT =
            "SELECT transaction_id, account_id, journey_id, amount, balance_after, created_at FROM fare_transactions";

    private final JdbcDatabase db;

    public JdbcFareTransactionRepository(JdbcDatabase db) {
        this.db = db;
    }

    @Override
    public Optional<FareTransaction> findByReference(String reference) {
        try (PreparedStatement statement = db.connection().prepareStatement(SELECT + " WHERE reference = ?")) {
            statement.setString(1, reference);
            List<FareTransaction> found = many(statement);
            return found.isEmpty() ? Optional.empty() : Optional.of(found.get(0));
        } catch (SQLException e) {
            throw Sql.failure("fare_transactions", e);
        }
    }

    @Override
    public FareTransaction insert(FareTransaction draft) {
        try {
            long id = Sql.nextId(db.connection(), "fare_transactions");
            try (PreparedStatement statement = db.connection().prepareStatement(
                    "INSERT INTO fare_transactions (transaction_id, reference, account_id, journey_id, amount, "
                            + "balance_after, created_at) VALUES (?, ?, ?, ?, ?, ?, ?)")) {
                statement.setLong(1, id);
                statement.setString(2, draft.getReference());
                statement.setLong(3, draft.getAccountId());
                statement.setLong(4, draft.getJourneyId());
                statement.setLong(5, draft.getAmount());
                statement.setLong(6, draft.getBalanceAfter());
                Sql.setInstant(statement, 7, draft.getCreatedAt());
                statement.executeUpdate();
            }
            return draft.withId(id);
        } catch (SQLException e) {
            throw Sql.refusal(e, "fare_transactions", "reference or journey_id", "account_id or journey_id",
                    "amount >= 0");
        }
    }

    /** Newest first. */
    @Override
    public List<FareTransaction> listForAccount(long accountId) {
        try (PreparedStatement statement = db.connection().prepareStatement(
                SELECT + " WHERE account_id = ? ORDER BY transaction_id DESC")) {
            statement.setLong(1, accountId);
            return many(statement);
        } catch (SQLException e) {
            throw Sql.failure("fare_transactions", e);
        }
    }

    /** The reference of a charge is always made from its journey id, so it is not read back separately. */
    private static List<FareTransaction> many(PreparedStatement statement) throws SQLException {
        try (ResultSet rows = statement.executeQuery()) {
            List<FareTransaction> found = new ArrayList<>();
            while (rows.next()) {
                found.add(FareTransaction.forJourney(rows.getLong("journey_id"), rows.getLong("account_id"),
                                rows.getLong("amount"), rows.getLong("balance_after"), Sql.getInstant(rows, "created_at"))
                        .withId(rows.getLong("transaction_id")));
            }
            return found;
        }
    }
}
