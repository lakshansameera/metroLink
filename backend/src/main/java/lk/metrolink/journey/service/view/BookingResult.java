package lk.metrolink.journey.service.view;

/** The answer to a successful booking: the new ticket, one sentence to show, and whether credit covers the fare yet. */
public final class BookingResult {
    private final TicketView ticket;
    private final String message;
    private final long balance;
    private final boolean canPayNow;

    public BookingResult(TicketView ticket, String message, long balance, boolean canPayNow) {
        this.ticket = ticket;
        this.message = message;
        this.balance = balance;
        this.canPayNow = canPayNow;
    }

    public TicketView getTicket() { return ticket; }
    public String getMessage() { return message; }
    public long getBalance() { return balance; }
    public boolean isCanPayNow() { return canPayNow; }
}
