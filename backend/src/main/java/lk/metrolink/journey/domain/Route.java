package lk.metrolink.journey.domain;

import java.util.Comparator;
import java.util.List;

public class Route {

    private String routeNo;
    private String name;
    private List<RouteStop> stops;

    public Route(String routeNo, String name, List<RouteStop> stops) {
        if (stops.size() < 2) {
            throw new IllegalArgumentException("A route needs at least two stops: " + routeNo);
        }
        this.routeNo = routeNo;
        this.name = name;
        this.stops = stops.stream().sorted(Comparator.comparingInt(RouteStop::getSequenceNo)).toList();
    }

    public String getRouteNo() { return routeNo; }
    public String getName() { return name; }
    public List<RouteStop> getStops() { return stops; }
    public String getOriginStopId() { return stops.get(0).getStopId(); }
    public String getDestinationStopId() { return stops.get(stops.size() - 1).getStopId(); }

    public boolean hasStop(String stopId) {
        return stops.stream().anyMatch(s -> s.getStopId().equals(stopId));
    }

    public boolean isBefore(String stopId, String otherStopId) {
        return hasStop(stopId) && hasStop(otherStopId)
                && requireStop(stopId).getSequenceNo() < requireStop(otherStopId).getSequenceNo();
    }

    public double distanceKm(String boardingStopId, String exitStopId) {
        RouteStop boarding = requireStop(boardingStopId);
        RouteStop exit = requireStop(exitStopId);
        if (exit.getSequenceNo() <= boarding.getSequenceNo()) {
            throw new DomainException("Exit stop " + exitStopId + " must come after boarding stop "
                    + boardingStopId + " on route " + routeNo, "EXIT_NOT_AFTER_BOARDING", 400);
        }

        return Math.round((exit.getDistanceKm() - boarding.getDistanceKm()) * 1000) / 1000.0;
    }

    private RouteStop requireStop(String stopId) {
        return stops.stream()
                .filter(s -> s.getStopId().equals(stopId))
                .findFirst()
                .orElseThrow(() -> new DomainException("Stop " + stopId + " is not on route " + routeNo,
                        "STOP_NOT_ON_ROUTE", 400));
    }
}
