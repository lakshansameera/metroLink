package lk.metrolink.journey.domain;

public class Stop {

    private String stopId;
    private String name;
    private double latitude;
    private double longitude;

    public Stop(String stopId, String name, double latitude, double longitude) {
        this.stopId = stopId;
        this.name = name;
        this.latitude = latitude;
        this.longitude = longitude;
    }

    public String getStopId() { return stopId; }
    public String getName() { return name; }
    public double getLatitude() { return latitude; }
    public double getLongitude() { return longitude; }

}
