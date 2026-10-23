package lk.metrolink.journey.service;

import com.fasterxml.jackson.annotation.JsonInclude;
import lk.metrolink.journey.service.view.JourneyView;
import lk.metrolink.journey.service.view.TicketView;

/**
 * What the reader shows after a scan: a tone (which sound and colour), one sentence, and the figures behind it.
 * The server decides all of this; the screen only displays it. Values that do not apply are left out of the JSON.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class ReaderDisplay {

    /** The sound and colour: success beep, warning tone or error tone. */
    public enum Tone { SUCCESS, WARNING, ERROR }

    /** The money figures of a scan. Each one is present only when it means something for that scan. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static final class FareFigures {
        private final Long due;
        private final Long charged;
        private final Long balance;
        private final Long shortfall;
        private final Long required;

        public FareFigures(Long due, Long charged, Long balance, Long shortfall, Long required) {
            this.due = due;
            this.charged = charged;
            this.balance = balance;
            this.shortfall = shortfall;
            this.required = required;
        }

        public Long getDue() { return due; }
        public Long getCharged() { return charged; }
        public Long getBalance() { return balance; }
        public Long getShortfall() { return shortfall; }
        public Long getRequired() { return required; }
    }

    private final ScanOutcome outcome;
    private final Tone tone;
    private final String message;
    private final RejectionReason reason;
    private final TicketView ticket;
    private final JourneyView journey;
    private final FareFigures fare;
    private final boolean overtravelled;

    public ReaderDisplay(ScanOutcome outcome, Tone tone, String message, RejectionReason reason, TicketView ticket,
                         JourneyView journey, FareFigures fare, boolean overtravelled) {
        this.outcome = outcome;
        this.tone = tone;
        this.message = message;
        this.reason = reason;
        this.ticket = ticket;
        this.journey = journey;
        this.fare = fare;
        this.overtravelled = overtravelled;
    }

    public ScanOutcome getOutcome() { return outcome; }
    public Tone getTone() { return tone; }
    public String getMessage() { return message; }
    public RejectionReason getReason() { return reason; }
    public TicketView getTicket() { return ticket; }
    public JourneyView getJourney() { return journey; }
    public FareFigures getFare() { return fare; }
    public boolean isOvertravelled() { return overtravelled; }
}
