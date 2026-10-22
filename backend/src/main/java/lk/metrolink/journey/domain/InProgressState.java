package lk.metrolink.journey.domain;

public class InProgressState implements JourneyState{

    @Override
    public String name() {
        return "IN_PROGRESS";
    }

    @Override
    public boolean isOpen() {
        return true;
    }

    @Override
    public JourneyState recordExit(long journeyId) {
        return this;
    }

    @Override
    public JourneyState markPaymentPending(long journeyId) {
        return new PaymentPendingState();
    }

    @Override
    public JourneyState complete(long journeyId) {
        return new CompletedState();
    }
}
