package lk.metrolink.journey.repository.memory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import lk.metrolink.journey.domain.FareTransaction;
import lk.metrolink.journey.repository.ConstraintViolationException;
import lk.metrolink.journey.repository.FareTransactionRepository;

public final class InMemoryFareTransactionRepository implements FareTransactionRepository {
    private final InMemoryDatabase db;

    public InMemoryFareTransactionRepository(InMemoryDatabase db) {
        this.db = db;
    }

    @Override
    public Optional<FareTransaction> findByReference(String reference) {
        return db.tables().fareTransactions.values().stream()
                .filter(x -> x.getReference().equals(reference))
                .findFirst();
    }

    @Override
    public FareTransaction insert(FareTransaction draft) {
        InMemoryDatabase.Tables t = db.tables();
        if (!t.accounts.containsKey(draft.getAccountId())) {
            throw new ConstraintViolationException("fare_transactions: FOREIGN KEY account_id failed");
        }
        if (!t.journeys.containsKey(draft.getJourneyId())) {
            throw new ConstraintViolationException("fare_transactions: FOREIGN KEY journey_id failed");
        }
        boolean duplicate = t.fareTransactions.values().stream().anyMatch(
                x -> x.getReference().equals(draft.getReference()) || x.getJourneyId() == draft.getJourneyId());
        if (duplicate) {
            throw new ConstraintViolationException(
                    "fare_transactions: UNIQUE reference or journey_id failed (" + draft.getReference() + ")");
        }
        FareTransaction stored = draft.withId(t.nextTransactionId++);
        t.fareTransactions.put(stored.getTransactionId(), stored);
        return stored;
    }

    @Override
    public List<FareTransaction> listForAccount(long accountId) {
        List<FareTransaction> rows = new ArrayList<>();
        for (FareTransaction x : db.tables().fareTransactions.values()) {
            if (x.getAccountId() == accountId) {
                rows.add(x);
            }
        }
        Collections.reverse(rows);                 // newest first
        return rows;
    }
}
