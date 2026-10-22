package lk.metrolink.journey.domain;

public class InsufficientCreditException extends DomainException {
    private static final long serialVersionUID = 1L;

    private final long required;
    private final long available;

    public InsufficientCreditException(long required, long available) {
        super("Insufficient credit: need LKR " + required + ", have LKR " + available,
              "INSUFFICIENT_CREDIT", 422);
        this.required = required;
        this.available = available;
    }

    public long required() { return required; }

    public long available() { return available; }
}
