package lk.metrolink.journey.domain;

public interface JourneyState {
    String name();

    default boolean isOpen() {
        return false;
    }

    /** True once the fare is paid and nothing more can happen to the journey. */
    default boolean isSettled() {
        return false;
    }

    /** True while the passenger has left but the fare is still unpaid. */
    default boolean isAwaitingPayment() {
        return false;
    }

    private DomainException illegal(long journeyId, String action) {
        return new DomainException("Journey " + journeyId + " is " + name() + "; cannot " + action,
                "ILLEGAL_TRANSITION", 409);
    }

    default JourneyState recordExit(long journeyId) {
        throw illegal(journeyId, "record an exit");
    }

    default JourneyState markPaymentPending(long journeyId) {
        throw illegal(journeyId, "mark payment pending");
    }

    default JourneyState complete(long journeyId) {
        throw illegal(journeyId, "complete");
    }

    static JourneyState of(String name) {
        switch (name) {
            case "IN_PROGRESS":     return new InProgressState();
            case "PAYMENT_PENDING": return new PaymentPendingState();
            case "COMPLETED":       return new CompletedState();
            default: throw new IllegalArgumentException("Unknown journey status: " + name);
        }
    }
}
