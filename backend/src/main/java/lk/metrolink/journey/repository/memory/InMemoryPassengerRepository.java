package lk.metrolink.journey.repository.memory;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lk.metrolink.journey.domain.Passenger;
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
    public List<Passenger> list() {
        return new ArrayList<>(db.tables().passengers.values());
    }
}
