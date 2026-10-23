package lk.metrolink.journey.domain;

import java.util.List;

/** A booking with one or more wrong fields (rule T1). Carries every problem, not just the first. */
public class InvalidBookingException extends DomainException {
    private static final long serialVersionUID = 1L;

    private final transient List<BookingRequest.Problem> problems;

    public InvalidBookingException(List<BookingRequest.Problem> problems) {
        super("Invalid request", "VALIDATION", 400);
        this.problems = List.copyOf(problems);
    }

    public List<BookingRequest.Problem> problems() {
        return problems;
    }
}
