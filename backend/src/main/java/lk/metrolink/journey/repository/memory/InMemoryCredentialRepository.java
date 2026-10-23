package lk.metrolink.journey.repository.memory;

import java.util.Optional;
import lk.metrolink.journey.domain.PasswordHash;
import lk.metrolink.journey.repository.ConstraintViolationException;
import lk.metrolink.journey.repository.CredentialRepository;

public final class InMemoryCredentialRepository implements CredentialRepository {
    private final InMemoryDatabase db;

    public InMemoryCredentialRepository(InMemoryDatabase db) {
        this.db = db;
    }

    @Override
    public void insert(long passengerId, PasswordHash hash) {
        InMemoryDatabase.Tables t = db.tables();
        if (!t.passengers.containsKey(passengerId)) {
            throw new ConstraintViolationException("credentials: FOREIGN KEY passenger_id failed");
        }
        if (t.credentials.containsKey(passengerId)) {
            throw new ConstraintViolationException("credentials: UNIQUE passenger_id failed (" + passengerId + ")");
        }
        t.credentials.put(passengerId, hash);
    }

    @Override
    public Optional<PasswordHash> findByPassenger(long passengerId) {
        return Optional.ofNullable(db.tables().credentials.get(passengerId));
    }
}
