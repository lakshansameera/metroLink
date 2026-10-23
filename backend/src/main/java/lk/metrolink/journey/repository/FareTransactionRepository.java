package lk.metrolink.journey.repository;

import java.util.List;
import java.util.Optional;
import lk.metrolink.journey.domain.FareTransaction;

public interface FareTransactionRepository {
    Optional<FareTransaction> findByReference(String reference);
    FareTransaction insert(FareTransaction draft);

    List<FareTransaction> listForAccount(long accountId);
}