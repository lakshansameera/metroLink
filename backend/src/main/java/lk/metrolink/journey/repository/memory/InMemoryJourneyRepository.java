package lk.metrolink.journey.repository.memory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import lk.metrolink.journey.domain.Journey;
import lk.metrolink.journey.domain.QRTicket;
import lk.metrolink.journey.repository.ConstraintViolationException;
import lk.metrolink.journey.repository.JourneyRepository;

public final class InMemoryJourneyRepository implements JourneyRepository {
    private final InMemoryDatabase db;

    public InMemoryJourneyRepository(InMemoryDatabase db) {
        this.db = db;
    }

    @Override
    public Optional<Journey> findById(long journeyId) {
        return Optional.ofNullable(db.tables().journeys.get(journeyId)).map(Journey::copy);
    }

    @Override
    public Optional<Journey> findByTicketId(long ticketId) {
        return db.tables().journeys.values().stream()
                .filter(j -> j.getTicketId() == ticketId)
                .findFirst()
                .map(Journey::copy);
    }

    @Override
    public List<Journey> list() {
        List<Journey> rows = new ArrayList<>();
        for (Journey j : db.tables().journeys.values()) {
            rows.add(j.copy());
        }
        Collections.reverse(rows);                 // newest first
        return rows;
    }

    @Override
    public List<Journey> listForPassenger(long passengerId) {
        InMemoryDatabase.Tables t = db.tables();
        return list().stream().filter(j -> {
            QRTicket ticket = t.tickets.get(j.getTicketId());
            return ticket != null && ticket.getPassengerId() == passengerId;
        }).toList();
    }

    @Override
    public Journey insert(Journey draft) {
        InMemoryDatabase.Tables t = db.tables();
        if (!t.tickets.containsKey(draft.getTicketId())) {
            throw new ConstraintViolationException("journeys: FOREIGN KEY ticket_id failed");
        }
        if (t.journeys.values().stream().anyMatch(j -> j.getTicketId() == draft.getTicketId())) {
            throw new ConstraintViolationException("journeys: UNIQUE ticket_id failed (" + draft.getTicketId() + ")");
        }
        Journey stored = draft.withId(t.nextJourneyId++);
        t.journeys.put(stored.getJourneyId(), stored);
        return stored.copy();
    }

    @Override
    public void save(Journey journey) {
        InMemoryDatabase.Tables t = db.tables();
        if (!t.journeys.containsKey(journey.getJourneyId())) {
            throw new ConstraintViolationException("journeys: no journey " + journey.getJourneyId());
        }
        t.journeys.put(journey.getJourneyId(), journey.copy());
    }
}
