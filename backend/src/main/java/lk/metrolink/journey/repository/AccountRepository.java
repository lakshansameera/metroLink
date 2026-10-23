package lk.metrolink.journey.repository;

import java.util.Optional;
import lk.metrolink.journey.domain.Account;

public interface AccountRepository {
    Optional<Account> findById(long accountId);

    Optional<Account> findByPassenger(long passengerId);
    void save(Account account);
}