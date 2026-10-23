package lk.metrolink.journey.domain;

import java.time.Instant;
import lk.metrolink.journey.log.Log;

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
        Log.info(Log.FACTORY, "Journey.start: new journey for ticket #%d at stop %s, starts in the IN_PROGRESS state",
                ticketId, boardingStopId);
        return new Journey(0, ticketId, routeNo, boardingStopId, tapInTime, null, null, null, null,
                new InProgressState());
    }

    public String status() {
        return state.name();
    }

    public boolean isOpen() {
        return state.isOpen();
    }

    public boolean isSettled() {
        return state.isSettled();
    }

    public boolean isAwaitingPayment() {
        return state.isAwaitingPayment();
    }

    public void recordExit(String exitStopId, Instant tapOutTime, long fareDue) {
        JourneyState before = state;
        state = before.recordExit(journeyId);
        this.exitStopId = exitStopId;
        this.tapOutTime = tapOutTime;
        this.fareDue = fareDue;
        Log.info(Log.STATE, "Journey #%d: %s -> %s (%s.recordExit: left at %s, fare due LKR %d)", journeyId,
                before.name(), state.name(), before.getClass().getSimpleName(), exitStopId, fareDue);
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
        Log.info(Log.STATE, "Journey #%d: %s -> %s (%s.markPaymentPending: the fare of LKR %d is unpaid)", journeyId,
                state.name(), next.name(), state.getClass().getSimpleName(), fareDue);
        state = next;
    }

    public void complete(long fareCharged) {
        JourneyState next = state.complete(journeyId);
        requireExitRecorded();
        this.fareCharged = fareCharged;
        Log.info(Log.STATE, "Journey #%d: %s -> %s (%s.complete: LKR %d charged)", journeyId, state.name(),
                next.name(), state.getClass().getSimpleName(), fareCharged);
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
