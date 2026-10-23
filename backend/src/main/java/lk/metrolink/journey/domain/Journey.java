package lk.metrolink.journey.domain;

import java.time.Instant;

public class Journey {

    private long journeyId;
    private long ticketId;
    private String routeNo;
    private String boardingStopId;
    private Instant tapInTime;
    private String exitStopId;
    private Instant tapOutTime;
    private Long fareDue;
    private Long fareCharged;
    private JourneyState state;

    public Journey(long journeyId, long ticketId, String routeNo, String boardingStopId, Instant tapInTime, String exitStopId, Instant tapOutTime, Long fareDue, Long fareCharged, JourneyState state) {
        this.journeyId = journeyId;
        this.ticketId = ticketId;
        this.routeNo = routeNo;
        this.boardingStopId = boardingStopId;
        this.tapInTime = tapInTime;
        this.exitStopId = exitStopId;
        this.tapOutTime = tapOutTime;
        this.fareDue = fareDue;
        this.fareCharged = fareCharged;
        this.state = state;
    }

    public static Journey start(long ticketId, String routeNo, String boardingStopId, Instant tapInTime) {
        return new Journey(0, ticketId, routeNo, boardingStopId, tapInTime, null, null, null, null,
                new InProgressState());
    }

    public String status() {
        return state.name();
    }

    public boolean isOpen() {
        return state.isOpen();
    }

    public void recordExit(String exitStopId, Instant tapOutTime, long fareDue) {
        state = state.recordExit(journeyId);
        this.exitStopId = exitStopId;
        this.tapOutTime = tapOutTime;
        this.fareDue = fareDue;
    }

    private void requireExitRecorded() {
        if (fareDue == null) {
            throw new DomainException("Journey " + journeyId + " has no recorded exit yet",
                    "ILLEGAL_TRANSITION", 409);
        }
    }

    public void markPaymentPending() {
        JourneyState next = state.markPaymentPending(journeyId);
        requireExitRecorded();
        state = next;
    }

    public void complete(long fareCharged) {
        JourneyState next = state.complete(journeyId);
        requireExitRecorded();
        this.fareCharged = fareCharged;
        state = next;
    }

    public long getJourneyId() {
        return journeyId;
    }

    public long getTicketId() {
        return ticketId;
    }

    public String getRouteNo() {
        return routeNo;
    }

    public String getBoardingStopId() {
        return boardingStopId;
    }

    public Instant getTapInTime() {
        return tapInTime;
    }

    public String getExitStopId() {
        return exitStopId;
    }

    public Instant getTapOutTime() {
        return tapOutTime;
    }

    public Long getFareDue() {
        return fareDue;
    }

    public Long getFareCharged() {
        return fareCharged;
    }

    public JourneyState getState() {
        return state;
    }

    public Journey withId(long newJourneyId) {
        return new Journey(newJourneyId, ticketId, routeNo, boardingStopId, tapInTime, exitStopId, tapOutTime,
                fareDue, fareCharged, state);
    }

    public Journey copy() {
        return withId(journeyId);
    }
}
