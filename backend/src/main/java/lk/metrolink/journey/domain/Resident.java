package lk.metrolink.journey.domain;

import lk.metrolink.journey.log.Log;

public class Resident implements Residency{

    @Override
    public String name() {
        return "RESIDENT";
    }

    @Override
    public Account openAccount(long accountId, long passengerId) {
        Log.info(Log.FACTORY, "Resident.openAccount: passenger #%d is a resident -> a new PermanentAccount (no end date)",
                passengerId);
        return new PermanentAccount(0, passengerId, accountId);
    }
}
