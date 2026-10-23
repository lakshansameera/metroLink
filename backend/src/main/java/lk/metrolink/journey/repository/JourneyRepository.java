package lk.metrolink.journey.repository;

import java.util.List;
import java.util.Optional;
import lk.metrolink.journey.domain.Journey;

public interface JourneyRepository {
    Optional<Journey> findById(long journeyId);


    Optional<Journey> findByTicketId(long ticketId);


    List<Journey> list();

    List<Journey> listForPassenger(long passengerId);

     Journey insert(Journey draft);

     void save(Journey journey);
}