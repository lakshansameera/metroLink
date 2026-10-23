package lk.metrolink.journey.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import lk.metrolink.journey.log.Log;

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
        Log.info(Log.FACTORY, "QRTicket.issue: new ticket %s for passenger #%d, route %s %s to %s on %s, fare LKR %d,"
                + " starts in the ACTIVE state", qrCode, passengerId, routeNo, boardingStopId,
                plannedDestinationStopId, travelDate, quotedFare);
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
        Optional<InvalidTicketReason> reason = state.refusalReason(travelDate, today);
        Log.info(Log.STATE, "Ticket #%d %s is %s: %s.refusalReason(travel date %s, today %s) -> %s",
                ticketId, qrCode, state.name(), state.getClass().getSimpleName(), travelDate, today,
                reason.isPresent() ? reason.get().name() : "usable");
        return reason;
    }

    public void markUsed() {
        TicketState before = state;
        state = before.markUsed(qrCode);
        Log.info(Log.STATE, "Ticket #%d %s: %s -> %s (%s.markUsed)", ticketId, qrCode, before.name(), state.name(),
                before.getClass().getSimpleName());
    }

    public QRTicket withId(long newTicketId) {
        return new QRTicket(newTicketId, qrCode, passengerId, routeNo, boardingStopId, plannedDestinationStopId,
                travelDate, quotedFare, issuedAt, state);
    }

    public QRTicket copy() {
        return withId(ticketId);
    }

}
