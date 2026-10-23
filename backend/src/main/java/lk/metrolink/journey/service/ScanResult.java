package lk.metrolink.journey.service;

import lk.metrolink.journey.domain.Journey;
import lk.metrolink.journey.domain.QRTicket;
import lk.metrolink.journey.domain.TripComparison;

/**
 * The structured answer to a scan or a settlement. The Reader turns it into a tone and a sentence.
 * Build one with {@code ScanResult.of(outcome)...build()}, so nobody has to remember the order of ten arguments.
 */
public final class ScanResult {
    private final ScanOutcome outcome;
    private final RejectionReason reason;
    private final String detail;
    private final Journey journey;
    private final QRTicket ticket;
    private final Long fareDue;
    private final Long fareCharged;
    private final Long balance;
    private final Long shortfall;
    private final Long required;
    private final TripComparison trip;

    private ScanResult(Builder b) {
        this.outcome = b.outcome;
        this.reason = b.reason;
        this.detail = b.detail;
        this.journey = b.journey;
        this.ticket = b.ticket;
        this.fareDue = b.fareDue;
        this.fareCharged = b.fareCharged;
        this.balance = b.balance;
        this.shortfall = b.shortfall;
        this.required = b.required;
        this.trip = b.trip;
    }

    public static Builder of(ScanOutcome outcome) {
        return new Builder(outcome);
    }

    /** The answer in one line for the console log: the outcome, then every figure that is set. */
    public String summary() {
        StringBuilder out = new StringBuilder(outcome.name());
        if (reason != null) out.append(" (").append(reason.name()).append(")");
        if (required != null) out.append(", needs LKR ").append(required);
        if (fareDue != null) out.append(", fare due LKR ").append(fareDue);
        if (fareCharged != null) out.append(", charged LKR ").append(fareCharged);
        if (balance != null) out.append(", balance LKR ").append(balance);
        if (shortfall != null) out.append(", short by LKR ").append(shortfall);
        if (trip != null && trip.kind() != null) out.append(", trip ").append(trip.kind());
        else if (trip != null && trip.boardedOffTicket()) out.append(", boarded off the ticket");
        return out.toString();
    }

    public ScanOutcome getOutcome() { return outcome; }
    /** Why the scan was refused, or why a fare is waiting. Null when there is nothing to explain. */
    public RejectionReason getReason() { return reason; }
    public String getDetail() { return detail; }
    public Journey getJourney() { return journey; }
    public QRTicket getTicket() { return ticket; }
    public Long getFareDue() { return fareDue; }
    public Long getFareCharged() { return fareCharged; }
    public Long getBalance() { return balance; }
    public Long getShortfall() { return shortfall; }
    public Long getRequired() { return required; }
    /**
     * How the real trip compares with the ticket (rules X7, X9, X10), or null when the scan was refused.
     * Over-travel and a fare above the ticket's are flags, not failures.
     */
    public TripComparison getTrip() { return trip; }
    /** True when the passenger went outside the ticket's stretch of road (rule X7). A flag, not a failure. */
    public boolean isOvertravelled() { return trip != null && trip.isOverTravel(); }

    public static final class Builder {
        private final ScanOutcome outcome;
        private RejectionReason reason;
        private String detail;
        private Journey journey;
        private QRTicket ticket;
        private Long fareDue;
        private Long fareCharged;
        private Long balance;
        private Long shortfall;
        private Long required;
        private TripComparison trip;

        private Builder(ScanOutcome outcome) {
            this.outcome = outcome;
        }

        public Builder reason(RejectionReason v) { this.reason = v; return this; }
        public Builder detail(String v) { this.detail = v; return this; }
        public Builder journey(Journey v) { this.journey = v; return this; }
        public Builder ticket(QRTicket v) { this.ticket = v; return this; }
        public Builder fareDue(Long v) { this.fareDue = v; return this; }
        public Builder fareCharged(Long v) { this.fareCharged = v; return this; }
        public Builder balance(Long v) { this.balance = v; return this; }
        public Builder shortfall(Long v) { this.shortfall = v; return this; }
        public Builder required(Long v) { this.required = v; return this; }
        public Builder trip(TripComparison v) { this.trip = v; return this; }

        public ScanResult build() {
            return new ScanResult(this);
        }
    }
}
