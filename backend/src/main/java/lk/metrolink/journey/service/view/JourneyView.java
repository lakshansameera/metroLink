package lk.metrolink.journey.service.view;

import java.time.Instant;

/** A journey with the names a person would want to read next to the ids. */
public final class JourneyView {
    private final long journeyId;
    private final long ticketId;
    private final String qrCode;
    private final long passengerId;
    private final String passengerName;
    private final String routeNo;
    private final String boardingStopId;
    private final String boardingStopName;
    private final String exitStopId;
    private final String exitStopName;
    private final String plannedDestinationStopId;
    private final Instant tapInTime;
    private final Instant tapOutTime;
    private final Long fareDue;
    private final Long fareCharged;
    private final String status;
    private final boolean overtravelled;

    public JourneyView(long journeyId, long ticketId, String qrCode, long passengerId, String passengerName,
                       String routeNo, String boardingStopId, String boardingStopName, String exitStopId,
                       String exitStopName, String plannedDestinationStopId, Instant tapInTime, Instant tapOutTime,
                       Long fareDue, Long fareCharged, String status, boolean overtravelled) {
        this.journeyId = journeyId;
        this.ticketId = ticketId;
        this.qrCode = qrCode;
        this.passengerId = passengerId;
        this.passengerName = passengerName;
        this.routeNo = routeNo;
        this.boardingStopId = boardingStopId;
        this.boardingStopName = boardingStopName;
        this.exitStopId = exitStopId;
        this.exitStopName = exitStopName;
        this.plannedDestinationStopId = plannedDestinationStopId;
        this.tapInTime = tapInTime;
        this.tapOutTime = tapOutTime;
        this.fareDue = fareDue;
        this.fareCharged = fareCharged;
        this.status = status;
        this.overtravelled = overtravelled;
    }

    public long getJourneyId() { return journeyId; }
    public long getTicketId() { return ticketId; }
    public String getQrCode() { return qrCode; }
    public long getPassengerId() { return passengerId; }
    public String getPassengerName() { return passengerName; }
    public String getRouteNo() { return routeNo; }
    public String getBoardingStopId() { return boardingStopId; }
    public String getBoardingStopName() { return boardingStopName; }
    public String getExitStopId() { return exitStopId; }
    public String getExitStopName() { return exitStopName; }
    public String getPlannedDestinationStopId() { return plannedDestinationStopId; }
    public Instant getTapInTime() { return tapInTime; }
    public Instant getTapOutTime() { return tapOutTime; }
    public Long getFareDue() { return fareDue; }
    public Long getFareCharged() { return fareCharged; }
    public String getStatus() { return status; }
    /** True when the passenger got off after the planned stop. */
    public boolean isOvertravelled() { return overtravelled; }
}
