package lk.metrolink.journey.service.view;

import lk.metrolink.journey.domain.TripComparison;

/**
 * The real trip next to the ticket, ready for the screen: what kind of trip it was, whether the stops differ from the
 * ticket's, and how the fare compares with the ticket's fare. The server works all of this out; the screen only shows it.
 */
public final class TripView {
    private final String kind;
    private final boolean boardedOffTicket;
    private final boolean exitedOffTicket;
    private final long quotedFare;
    private final Long fareDifference;
    private final boolean overcharged;

    private TripView(String kind, boolean boardedOffTicket, boolean exitedOffTicket, long quotedFare,
                     Long fareDifference, boolean overcharged) {
        this.kind = kind;
        this.boardedOffTicket = boardedOffTicket;
        this.exitedOffTicket = exitedOffTicket;
        this.quotedFare = quotedFare;
        this.fareDifference = fareDifference;
        this.overcharged = overcharged;
    }

    public static TripView of(TripComparison trip, Long fareDue) {
        return new TripView(trip.kindName(), trip.boardedOffTicket(), trip.exitedOffTicket(), trip.quotedFare(),
                trip.fareDifference(fareDue), trip.isOverCharged(fareDue));
    }

    /** AS_TICKETED, SHORTER or OVER_TRAVEL; null while the passenger is on the bus and nothing is outside the ticket yet. */
    public String getKind() { return kind; }
    /** Tapped in at a stop other than the one on the ticket. */
    public boolean isBoardedOffTicket() { return boardedOffTicket; }
    /** Tapped out at a stop other than the planned one. */
    public boolean isExitedOffTicket() { return exitedOffTicket; }
    /** The fare printed on the ticket. */
    public long getQuotedFare() { return quotedFare; }
    /** Fare owed minus the ticket's fare (positive: more than the ticket, negative: less). Null until the exit. */
    public Long getFareDifference() { return fareDifference; }
    /** The fare owed is higher than the ticket's fare. */
    public boolean isOvercharged() { return overcharged; }
}
