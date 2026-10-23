package lk.metrolink.journey.domain;

public class PaymentPendingState implements JourneyState {
    @Override
    public String name() {
        return "PAYMENT_PENDING";
    }

    @Override
    public boolean isAwaitingPayment() {
        return true;
    }

    @Override
    public JourneyState complete(long journeyId) {
        return new CompletedState();
    }
}
