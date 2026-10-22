package lk.metrolink.journey.domain;

public class CompletedState implements JourneyState{

    @Override
    public String name() {
        return "COMPLETED";
    }

}
