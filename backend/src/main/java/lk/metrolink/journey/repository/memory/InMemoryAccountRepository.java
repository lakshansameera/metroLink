package lk.metrolink.journey.repository.memory;

import java.util.Optional;
import lk.metrolink.journey.domain.Account;
import lk.metrolink.journey.repository.AccountRepository;
import lk.metrolink.journey.repository.ConstraintViolationException;

public final class InMemoryAccountRepository implements AccountRepository {
    private final InMemoryDatabase db;

    public InMemoryAccountRepository(InMemoryDatabase db) {
        this.db = db;
    }

    @Override
    public Optional<Account> findById(long accountId) {
        return Optional.ofNullable(db.tables().accounts.get(accountId)).map(Account::copy);
    }

    @Override
    public Optional<Account> findByPassenger(long passengerId) {
        return db.tables().accounts.values().stream()
                .filter(a -> a.getPassengerId() == passengerId)
                .findFirst()
                .map(Account::copy);
    }

    @Override
    public void save(Account account) {
        InMemoryDatabase.Tables t = db.tables();
        if (!t.accounts.containsKey(account.getAccountId())) {
            throw new ConstraintViolationException("accounts: no account " + account.getAccountId());
        }
        if (account.getBalance() < 0) {
            throw new ConstraintViolationException("accounts: CHECK balance >= 0 failed");
        }
        t.accounts.put(account.getAccountId(), account.copy());
    }
}
