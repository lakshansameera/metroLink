package lk.metrolink.journey.repository;

import java.util.List;
import java.util.Optional;
import lk.metrolink.journey.domain.Route;
import lk.metrolink.journey.domain.Stop;

public interface RouteRepository {
    Optional<Stop> findStop(String stopId);

    List<Stop> listStops();

    Optional<Route> findRoute(String routeNo);

    List<Route> listRoutes();
}