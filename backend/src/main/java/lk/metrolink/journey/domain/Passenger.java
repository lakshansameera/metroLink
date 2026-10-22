package lk.metrolink.journey.domain;

public class Passenger {

    private long passengerId;
    private String name;
    private String email;
    private Residency residency;

    public Passenger(long passengerId, String name, String email, Residency residency) {
        this.passengerId = passengerId;
        this.name = name;
        this.email = email;
        this.residency = residency;
    }

    public long getPassengerId() {
        return passengerId;
    }

    public String getName() {
        return name;
    }

    public String getEmail() {
        return email;
    }

    public Residency getResidency() {
        return residency;
    }

    public Account openAccount(long accountId) {
        return residency.openAccount(accountId, passengerId);
    }
}
