package lk.metrolink.journey.service.view;

import java.time.Instant;
import java.time.LocalDate;

/** A ticket with the names a person would want to read next to the ids. */
public final class TicketView {
    private final long ticketId;
    private final String qrCode;
    private final long passengerId;
    private final String passengerName;
    private final String routeNo;
    private final String boardingStopId;
    private final String boardingStopName;
    private final String plannedDestinationStopId;
    private final String plannedDestinationStopName;
    private final LocalDate travelDate;
    private final long quotedFare;
    private final String status;
    private final Instant issuedAt;

    public TicketView(long ticketId, String qrCode, long passengerId, String passengerName, String routeNo,
                      String boardingStopId, String boardingStopName, String plannedDestinationStopId,
                      String plannedDestinationStopName, LocalDate travelDate, long quotedFare, String status,
                      Instant issuedAt) {
        this.ticketId = ticketId;
        this.qrCode = qrCode;
        this.passengerId = passengerId;
        this.passengerName = passengerName;
        this.routeNo = routeNo;
        this.boardingStopId = boardingStopId;
        this.boardingStopName = boardingStopName;
        this.plannedDestinationStopId = plannedDestinationStopId;
        this.plannedDestinationStopName = plannedDestinationStopName;
        this.travelDate = travelDate;
        this.quotedFare = quotedFare;
        this.status = status;
        this.issuedAt = issuedAt;
    }

    public long getTicketId() { return ticketId; }
    public String getQrCode() { return qrCode; }
    public long getPassengerId() { return passengerId; }
    public String getPassengerName() { return passengerName; }
    public String getRouteNo() { return routeNo; }
    public String getBoardingStopId() { return boardingStopId; }
    public String getBoardingStopName() { return boardingStopName; }
    public String getPlannedDestinationStopId() { return plannedDestinationStopId; }
    public String getPlannedDestinationStopName() { return plannedDestinationStopName; }
    public LocalDate getTravelDate() { return travelDate; }
    public long getQuotedFare() { return quotedFare; }
    public String getStatus() { return status; }
    public Instant getIssuedAt() { return issuedAt; }
}
