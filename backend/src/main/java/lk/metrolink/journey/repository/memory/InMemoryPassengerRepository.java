package lk.metrolink.journey.repository.memory;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lk.metrolink.journey.domain.Passenger;
import lk.metrolink.journey.repository.ConstraintViolationException;
import lk.metrolink.journey.repository.PassengerRepository;

public final class InMemoryPassengerRepository implements PassengerRepository {
    private final InMemoryDatabase db;

    public InMemoryPassengerRepository(InMemoryDatabase db) {
        this.db = db;
    }

    @Override
    public Optional<Passenger> find(long passengerId) {
        return Optional.ofNullable(db.tables().passengers.get(passengerId));
    }

    @Override
    public Optional<Passenger> findByEmail(String email) {
        String wanted = email.trim();
        return db.tables().passengers.values().stream()
                .filter(p -> p.getEmail().equalsIgnoreCase(wanted))
                .findFirst();
    }

    @Override
    public List<Passenger> list() {
        return new ArrayList<>(db.tables().passengers.values());
    }

    @Override
    public Passenger insert(Passenger draft) {
        InMemoryDatabase.Tables t = db.tables();
        if (findByEmail(draft.getEmail()).isPresent()) {
            throw new ConstraintViolationException("passengers: UNIQUE email failed (" + draft.getEmail() + ")");
        }
        Passenger stored = draft.withId(t.nextPassengerId++);
        t.passengers.put(stored.getPassengerId(), stored);
        return stored;
    }
}
