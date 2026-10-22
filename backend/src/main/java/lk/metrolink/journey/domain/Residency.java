package lk.metrolink.journey.domain;

public interface Residency {
    String name();

    Account openAccount(long accountId, long passengerId);
}
