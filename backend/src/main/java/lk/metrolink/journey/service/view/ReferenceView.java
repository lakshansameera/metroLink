package lk.metrolink.journey.service.view;

import java.util.List;

/** The network: every stop, and every route with its stops in order. */
public final class ReferenceView {

    /** One stop on its own. */
    public static final class StopView {
        private final String stopId;
        private final String name;

        public StopView(String stopId, String name) {
            this.stopId = stopId;
            this.name = name;
        }

        public String getStopId() { return stopId; }
        public String getName() { return name; }
    }

    /** A stop as it sits on a route. */
    public static final class RouteStopView {
        private final String stopId;
        private final String name;
        private final int sequenceNo;
        private final double distanceKm;

        public RouteStopView(String stopId, String name, int sequenceNo, double distanceKm) {
            this.stopId = stopId;
            this.name = name;
            this.sequenceNo = sequenceNo;
            this.distanceKm = distanceKm;
        }

        public String getStopId() { return stopId; }
        public String getName() { return name; }
        public int getSequenceNo() { return sequenceNo; }
        public double getDistanceKm() { return distanceKm; }
    }

    /** A route with its stops in travel order. */
    public static final class RouteView {
        private final String routeNo;
        private final String name;
        private final List<RouteStopView> stops;

        public RouteView(String routeNo, String name, List<RouteStopView> stops) {
            this.routeNo = routeNo;
            this.name = name;
            this.stops = List.copyOf(stops);
        }

        public String getRouteNo() { return routeNo; }
        public String getName() { return name; }
        public List<RouteStopView> getStops() { return stops; }
    }

    private final List<StopView> stops;
    private final List<RouteView> routes;

    public ReferenceView(List<StopView> stops, List<RouteView> routes) {
        this.stops = List.copyOf(stops);
        this.routes = List.copyOf(routes);
    }

    public List<StopView> getStops() { return stops; }
    public List<RouteView> getRoutes() { return routes; }
}
