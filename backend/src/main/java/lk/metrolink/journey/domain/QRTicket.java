package lk.metrolink.journey.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

public class QRTicket {

    private long ticketId;
    private String qrCode;
    private long passengerId;
    private String routeNo;
    private String boardingStopId;
    private String plannedDestinationStopId;
    private LocalDate travelDate;
    private long quotedFare;
    private Instant issuedAt;
    private TicketState state;

    public QRTicket(long ticketId, String qrCode, long passengerId, String routeNo, String boardingStopId, String plannedDestinationStopId, LocalDate travelDate, long quotedFare, Instant issuedAt, TicketState state) {
        this.ticketId = ticketId;
        this.qrCode = qrCode;
        this.passengerId = passengerId;
        this.routeNo = routeNo;
        this.boardingStopId = boardingStopId;
        this.plannedDestinationStopId = plannedDestinationStopId;
        this.travelDate = travelDate;
        this.quotedFare = quotedFare;
        this.issuedAt = issuedAt;
        this.state = state;
    }

    /** A newly booked ticket: active, not yet stored (id 0), with no journey. */
    public static QRTicket issue(String qrCode, long passengerId, String routeNo, String boardingStopId,
                                 String plannedDestinationStopId, LocalDate travelDate, long quotedFare,
                                 Instant issuedAt) {
        return new QRTicket(0, qrCode, passengerId, routeNo, boardingStopId, plannedDestinationStopId, travelDate,
                quotedFare, issuedAt, TicketState.of("ACTIVE"));
    }

    public long getTicketId() {
        return ticketId;
    }

    public String getQrCode() {
        return qrCode;
    }

    public long getPassengerId() {
        return passengerId;
    }

    public String getRouteNo() {
        return routeNo;
    }

    public String getBoardingStopId() {
        return boardingStopId;
    }

    public String getPlannedDestinationStopId() {
        return plannedDestinationStopId;
    }

    public LocalDate getTravelDate() {
        return travelDate;
    }

    public long getQuotedFare() {
        return quotedFare;
    }

    public Instant getIssuedAt() {
        return issuedAt;
    }

    public TicketState getState() {
        return state;
    }

    public String status() {
        return state.name();
    }

    public Optional<InvalidTicketReason> refusalReason(LocalDate today) {
        return state.refusalReason(travelDate, today);
    }

    public void markUsed() {
        state = state.markUsed(qrCode);
    }

    public QRTicket withId(long newTicketId) {
        return new QRTicket(newTicketId, qrCode, passengerId, routeNo, boardingStopId, plannedDestinationStopId,
                travelDate, quotedFare, issuedAt, state);
    }

    public QRTicket copy() {
        return withId(ticketId);
    }

}
