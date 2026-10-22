package lk.metrolink.journey.domain;

public class Resident implements Residency{

    @Override
    public String name() {
        return "RESIDENT";
    }

    @Override
    public Account openAccount(long accountId, long passengerId) {
        return new PermanentAccount(0, passengerId, accountId);
    }
}
