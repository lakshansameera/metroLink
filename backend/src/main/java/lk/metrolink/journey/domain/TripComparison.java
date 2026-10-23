package lk.metrolink.journey.domain;

import java.util.List;

/**
 * How the trip a passenger really made compares with the trip the ticket was bought for (rules X7, X9, X10).
 *
 * <p>The ticket says "board at A, get off at B". The reader says where the passenger really tapped in and out.
 * Because the stops of a route are in order, one question settles it: did the real trip reach outside the ticket's
 * stretch of road?
 *
 * <ul>
 *   <li>{@link Kind#OVER_TRAVEL}: yes. The passenger boarded before the ticket's stop, or got off after the planned
 *       stop, or both.</li>
 *   <li>{@link Kind#SHORTER}: no, but the stops are not the ticket's. They boarded later or got off earlier.</li>
 *   <li>{@link Kind#AS_TICKETED}: the stops are the ticket's stops.</li>
 * </ul>
 *
 * <p>While the passenger is still on the bus the answer may not be known. Boarding before the ticket's stop already
 * makes it over-travel, but otherwise {@link #kind()} is null until they tap out.
 *
 * <p>This is a small immutable value object. It only compares and describes. It never changes a ticket or a journey
 * and it never moves money: over-travel is a flag, not a payment failure (X7), and the fare is always priced from the
 * stop the passenger really boarded at to the stop they really got off at (X3).
 */
public final class TripComparison {

    /** The result of the comparison. The names are sent to the screen as they are written here. */
    public enum Kind {
        AS_TICKETED, SHORTER, OVER_TRAVEL;

        public static List<String> names() {
            return List.of("AS_TICKETED", "SHORTER", "OVER_TRAVEL");
        }
    }

    private final Kind kind;
    private final boolean boardedOffTicket;
    private final boolean exitedOffTicket;
    private final String ticketFrom;
    private final String ticketTo;
    private final String boardedAt;
    private final String exitedAt;
    private final long quotedFare;

    private TripComparison(Kind kind, boolean boardedOffTicket, boolean exitedOffTicket, QRTicket ticket,
                           String boardedAt, String exitedAt) {
        this.kind = kind;
        this.boardedOffTicket = boardedOffTicket;
        this.exitedOffTicket = exitedOffTicket;
        this.ticketFrom = ticket.getBoardingStopId();
        this.ticketTo = ticket.getPlannedDestinationStopId();
        this.boardedAt = boardedAt;
        this.exitedAt = exitedAt;
        this.quotedFare = ticket.getQuotedFare();
    }

    /**
     * Compares the real trip with the ticket.
     *
     * @param boardedAt the stop where the passenger tapped in
     * @param exitedAt  the stop where they tapped out, or null while they are still on the bus
     */
    public static TripComparison of(Route route, QRTicket ticket, String boardedAt, String exitedAt) {
        boolean boardedOff = !boardedAt.equals(ticket.getBoardingStopId());
        boolean exitedOff = exitedAt != null && !exitedAt.equals(ticket.getPlannedDestinationStopId());

        boolean boardedBeforeTheTicket = route.isBefore(boardedAt, ticket.getBoardingStopId());
        boolean leftAfterThePlannedStop = exitedAt != null
                && route.isBefore(ticket.getPlannedDestinationStopId(), exitedAt);

        Kind kind;
        if (boardedBeforeTheTicket || leftAfterThePlannedStop) {
            kind = Kind.OVER_TRAVEL;
        } else if (exitedAt == null) {
            kind = null;                                    // still on the bus, nothing outside the ticket yet
        } else if (boardedOff || exitedOff) {
            kind = Kind.SHORTER;
        } else {
            kind = Kind.AS_TICKETED;
        }
        return new TripComparison(kind, boardedOff, exitedOff, ticket, boardedAt, exitedAt);
    }

    /** Null while the passenger is still on the bus and has not yet gone outside the ticket. */
    public Kind kind() { return kind; }
    public String kindName() { return kind == null ? null : kind.name(); }

    public boolean isOverTravel() { return kind == Kind.OVER_TRAVEL; }
    /** The passenger tapped in somewhere other than the stop on the ticket. */
    public boolean boardedOffTicket() { return boardedOffTicket; }
    /** The passenger tapped out somewhere other than the planned stop. False while they are still on the bus. */
    public boolean exitedOffTicket() { return exitedOffTicket; }
    public long quotedFare() { return quotedFare; }

    /**
     * The fare owed minus the fare on the ticket: positive means more than the ticket said, negative means less.
     * Null until the fare is worked out at the exit.
     */
    public Long fareDifference(Long fareDue) {
        return fareDue == null ? null : fareDue - quotedFare;
    }

    /** True when the fare owed is higher than the fare on the ticket. */
    public boolean isOverCharged(Long fareDue) {
        Long difference = fareDifference(fareDue);
        return difference != null && difference > 0;
    }

    /** One line for the console log, in stop ids, for example "ticket S1 to S4, trip S1 to S5: OVER_TRAVEL". */
    public String summary(Long fareDue) {
        StringBuilder out = new StringBuilder("ticket ").append(ticketFrom).append(" to ").append(ticketTo)
                .append(", trip ").append(boardedAt).append(" to ").append(exitedAt == null ? "?" : exitedAt)
                .append(": ").append(kind == null ? "not decided yet" : kind.name());
        if (boardedOffTicket) out.append(", boarded at another stop than the ticket");
        if (exitedOffTicket) out.append(", got off at another stop than the ticket");
        Long difference = fareDifference(fareDue);
        if (difference != null) {
            out.append(", fare LKR ").append(fareDue).append(" against the ticket's LKR ").append(quotedFare);
            if (difference > 0) out.append(" (LKR ").append(difference).append(" more, over-charged)");
            else if (difference < 0) out.append(" (LKR ").append(-difference).append(" less)");
            else out.append(" (the same)");
        }
        return out.toString();
    }
}
