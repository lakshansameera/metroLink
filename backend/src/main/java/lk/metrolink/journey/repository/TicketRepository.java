package lk.metrolink.journey.repository;

import java.util.List;
import java.util.Optional;
import lk.metrolink.journey.domain.QRTicket;

public interface TicketRepository {
    Optional<QRTicket> findById(long ticketId);

    Optional<QRTicket> findByQrCode(String qrCode);

    List<QRTicket> list();

    List<QRTicket> listForPassenger(long passengerId);

     QRTicket insert(QRTicket draft);

    void save(QRTicket ticket);
}