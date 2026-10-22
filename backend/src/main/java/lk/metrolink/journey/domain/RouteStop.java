package lk.metrolink.journey.domain;

public class RouteStop {
    private String stopId;
    private int sequenceNo;
    private double distanceKm;

    public RouteStop(String stopId, int sequenceNo, double distanceKm) {
        this.stopId = stopId;
        this.sequenceNo = sequenceNo;
        this.distanceKm = distanceKm;
    }

    public String getStopId() { return stopId; }
    public int getSequenceNo() { return sequenceNo; }
    public double getDistanceKm() { return distanceKm; }
}
