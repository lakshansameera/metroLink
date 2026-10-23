package lk.metrolink.journey.repository;

import java.util.List;
import java.util.Optional;
import lk.metrolink.journey.domain.Passenger;

public interface PassengerRepository {
    Optional<Passenger> find(long passengerId);

    /** Finds a passenger by email, ignoring case and surrounding spaces. */
    Optional<Passenger> findByEmail(String email);

    List<Passenger> list();

    /** Stores a new passenger and returns it with the real id. The email must not be registered already (rule O2). */
    Passenger insert(Passenger draft);
}
