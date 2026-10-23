package lk.metrolink.journey.repository;

import java.util.List;
import java.util.Optional;
import lk.metrolink.journey.domain.Passenger;

public interface PassengerRepository {
    Optional<Passenger> find(long passengerId);

    List<Passenger> list();
}