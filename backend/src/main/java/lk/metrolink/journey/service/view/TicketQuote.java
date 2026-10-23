package lk.metrolink.journey.service.view;

/** What a trip would cost, and whether the customer's account could pay it right now. Nothing is booked or charged. */
public final class TicketQuote {
    private final long fare;
    private final double distanceKm;
    private final String fareType;
    private final long balance;
    private final boolean canPayNow;

    public TicketQuote(long fare, double distanceKm, String fareType, long balance, boolean canPayNow) {
        this.fare = fare;
        this.distanceKm = distanceKm;
        this.fareType = fareType;
        this.balance = balance;
        this.canPayNow = canPayNow;
    }

    public long getFare() { return fare; }
    public double getDistanceKm() { return distanceKm; }
    public String getFareType() { return fareType; }
    public long getBalance() { return balance; }
    public boolean isCanPayNow() { return canPayNow; }
}
