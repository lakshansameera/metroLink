package lk.metrolink.journey.repository.jdbc;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.Optional;
import lk.metrolink.journey.domain.Account;
import lk.metrolink.journey.domain.PermanentAccount;
import lk.metrolink.journey.domain.TemporaryAccount;
import lk.metrolink.journey.repository.AccountRepository;
import lk.metrolink.journey.repository.ConstraintViolationException;

/**
 * An account row holds only the balance. Whether the account is permanent or temporary, and when a temporary one
 * stops working, comes from the passenger it belongs to: a resident's is permanent, a non-resident's works until the
 * visa expires.
 */
public final class JdbcAccountRepository implements AccountRepository {
    private static final String SELECT =
            "SELECT a.account_id, a.passenger_id, a.balance, p.residency, p.visa_expiry "
                    + "FROM accounts a JOIN passengers p ON p.passenger_id = a.passenger_id";

    private final JdbcDatabase db;

    public JdbcAccountRepository(JdbcDatabase db) {
        this.db = db;
    }

    @Override
    public Optional<Account> findById(long accountId) {
        return one(" WHERE a.account_id = ?", accountId);
    }

    @Override
    public Optional<Account> findByPassenger(long passengerId) {
        return one(" WHERE a.passenger_id = ?", passengerId);
    }

    @Override
    public Account insert(Account draft) {
        requireKindMatchesPassenger(draft);
        try {
            long id = Sql.nextId(db.connection(), "accounts");
            try (PreparedStatement statement = db.connection().prepareStatement(
                    "INSERT INTO accounts (account_id, passenger_id, balance) VALUES (?, ?, ?)")) {
                statement.setLong(1, id);
                statement.setLong(2, draft.getPassengerId());
                statement.setLong(3, draft.getBalance());
                statement.executeUpdate();
            }
            return draft.withId(id);
        } catch (SQLException e) {
            throw Sql.refusal(e, "accounts", "passenger_id", "passenger_id", "balance >= 0");
        }
    }

    @Override
    public void save(Account account) {
        try (PreparedStatement statement = db.connection().prepareStatement(
                "UPDATE accounts SET balance = ? WHERE account_id = ?")) {
            statement.setLong(1, account.getBalance());
            statement.setLong(2, account.getAccountId());
            if (statement.executeUpdate() == 0) {
                throw new ConstraintViolationException("accounts: no account " + account.getAccountId());
            }
        } catch (SQLException e) {
            throw Sql.refusal(e, "accounts", "passenger_id", "passenger_id", "balance >= 0");
        }
    }

    /** The passenger must exist, and the account must be the kind (and have the visa date) their residency calls for. */
    private void requireKindMatchesPassenger(Account draft) {
        try (PreparedStatement statement = db.connection().prepareStatement(
                "SELECT residency, visa_expiry FROM passengers WHERE passenger_id = ?")) {
            statement.setLong(1, draft.getPassengerId());
            try (ResultSet row = statement.executeQuery()) {
                if (!row.next()) {
                    throw new ConstraintViolationException("accounts: FOREIGN KEY passenger_id failed");
                }
                boolean resident = "RESIDENT".equals(row.getString("residency"));
                LocalDate visaExpiry = Sql.getDate(row, "visa_expiry");
                boolean matches = draft instanceof TemporaryAccount temporary
                        ? !resident && temporary.getVisaExpiry().equals(visaExpiry)
                        : resident;
                if (!matches) {
                    throw new ConstraintViolationException(
                            "accounts: CHECK the kind of account matches the passenger's residency failed");
                }
            }
        } catch (SQLException e) {
            throw Sql.failure("accounts", e);
        }
    }

    private Optional<Account> one(String where, long id) {
        try (PreparedStatement statement = db.connection().prepareStatement(SELECT + where)) {
            statement.setLong(1, id);
            try (ResultSet row = statement.executeQuery()) {
                if (!row.next()) {
                    return Optional.empty();
                }
                long accountId = row.getLong("account_id");
                long passengerId = row.getLong("passenger_id");
                long balance = row.getLong("balance");
                return Optional.of("RESIDENT".equals(row.getString("residency"))
                        ? new PermanentAccount(balance, passengerId, accountId)
                        : new TemporaryAccount(balance, passengerId, accountId, Sql.getDate(row, "visa_expiry")));
            }
        } catch (SQLException e) {
            throw Sql.failure("accounts", e);
        }
    }
}
