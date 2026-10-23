package lk.metrolink.journey.repository.memory;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lk.metrolink.journey.domain.Route;
import lk.metrolink.journey.domain.Stop;
import lk.metrolink.journey.repository.RouteRepository;

public final class InMemoryRouteRepository implements RouteRepository {
    private final InMemoryDatabase db;

    public InMemoryRouteRepository(InMemoryDatabase db) {
        this.db = db;
    }

    @Override
    public Optional<Stop> findStop(String stopId) {
        return Optional.ofNullable(db.tables().stops.get(stopId));
    }

    @Override
    public List<Stop> listStops() {
        return new ArrayList<>(db.tables().stops.values());
    }

    @Override
    public Optional<Route> findRoute(String routeNo) {
        return Optional.ofNullable(db.tables().routes.get(routeNo));
    }

    @Override
    public List<Route> listRoutes() {
        return new ArrayList<>(db.tables().routes.values());
    }
}
