package lk.metrolink.journey.domain;

import java.util.List;
import lk.metrolink.journey.log.Log;

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
        Log.warn(Log.STATE, "Journey #%d is %s: %s has no way to %s, so the change is refused",
                journeyId, name(), getClass().getSimpleName(), action);
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

    /** Every state a journey can be in, spelled as it is stored and sent to the screen. */
    static List<String> names() {
        return List.of("IN_PROGRESS", "PAYMENT_PENDING", "COMPLETED");
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
