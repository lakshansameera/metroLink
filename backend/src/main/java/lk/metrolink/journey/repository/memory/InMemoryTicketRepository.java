package lk.metrolink.journey.repository.memory;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lk.metrolink.journey.domain.QRTicket;
import lk.metrolink.journey.repository.ConstraintViolationException;
import lk.metrolink.journey.repository.TicketRepository;

public final class InMemoryTicketRepository implements TicketRepository {
    private final InMemoryDatabase db;

    public InMemoryTicketRepository(InMemoryDatabase db) {
        this.db = db;
    }

    @Override
    public Optional<QRTicket> findById(long ticketId) {
        return Optional.ofNullable(db.tables().tickets.get(ticketId)).map(QRTicket::copy);
    }

    @Override
    public Optional<QRTicket> findByQrCode(String qrCode) {
        return db.tables().tickets.values().stream()
                .filter(t -> t.getQrCode().equals(qrCode))
                .findFirst()
                .map(QRTicket::copy);
    }

    @Override
    public List<QRTicket> list() {
        List<QRTicket> rows = new ArrayList<>();
        for (QRTicket t : db.tables().tickets.values()) {
            rows.add(t.copy());
        }
        return rows;
    }

    @Override
    public List<QRTicket> listForPassenger(long passengerId) {
        return list().stream().filter(t -> t.getPassengerId() == passengerId).toList();
    }

    @Override
    public QRTicket insert(QRTicket draft) {
        InMemoryDatabase.Tables t = db.tables();
        if (!t.passengers.containsKey(draft.getPassengerId())) {
            throw new ConstraintViolationException("tickets: FOREIGN KEY passenger_id failed");
        }
        if (t.tickets.values().stream().anyMatch(x -> x.getQrCode().equals(draft.getQrCode()))) {
            throw new ConstraintViolationException("tickets: UNIQUE qr_code failed (" + draft.getQrCode() + ")");
        }
        QRTicket stored = draft.withId(t.nextTicketId++);
        t.tickets.put(stored.getTicketId(), stored);
        return stored.copy();
    }

    @Override
    public void save(QRTicket ticket) {
        InMemoryDatabase.Tables t = db.tables();
        if (!t.tickets.containsKey(ticket.getTicketId())) {
            throw new ConstraintViolationException("tickets: no ticket " + ticket.getTicketId());
        }
        t.tickets.put(ticket.getTicketId(), ticket.copy());
    }
}
