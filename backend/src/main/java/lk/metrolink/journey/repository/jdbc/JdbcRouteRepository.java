package lk.metrolink.journey.repository.jdbc;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lk.metrolink.journey.domain.Route;
import lk.metrolink.journey.domain.RouteStop;
import lk.metrolink.journey.domain.Stop;
import lk.metrolink.journey.repository.RouteRepository;

/** Stops and routes. Read only: the network is maintained by the Transport Manager, not by this function. */
public final class JdbcRouteRepository implements RouteRepository {
    private final JdbcDatabase db;

    public JdbcRouteRepository(JdbcDatabase db) {
        this.db = db;
    }

    @Override
    public Optional<Stop> findStop(String stopId) {
        try (PreparedStatement statement = db.connection().prepareStatement(
                "SELECT stop_id, name, latitude, longitude FROM stops WHERE stop_id = ?")) {
            statement.setString(1, stopId);
            try (ResultSet row = statement.executeQuery()) {
                return row.next() ? Optional.of(stop(row)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw Sql.failure("stops", e);
        }
    }

    @Override
    public List<Stop> listStops() {
        try (PreparedStatement statement = db.connection().prepareStatement(
                "SELECT stop_id, name, latitude, longitude FROM stops ORDER BY stop_id");
             ResultSet rows = statement.executeQuery()) {
            List<Stop> found = new ArrayList<>();
            while (rows.next()) {
                found.add(stop(rows));
            }
            return found;
        } catch (SQLException e) {
            throw Sql.failure("stops", e);
        }
    }

    @Override
    public Optional<Route> findRoute(String routeNo) {
        try (PreparedStatement statement = db.connection().prepareStatement(
                "SELECT route_no, name FROM routes WHERE route_no = ?")) {
            statement.setString(1, routeNo);
            try (ResultSet row = statement.executeQuery()) {
                return row.next() ? Optional.of(route(row.getString("route_no"), row.getString("name"))) : Optional.empty();
            }
        } catch (SQLException e) {
            throw Sql.failure("routes", e);
        }
    }

    @Override
    public List<Route> listRoutes() {
        List<String[]> headers = new ArrayList<>();
        try (PreparedStatement statement = db.connection().prepareStatement(
                "SELECT route_no, name FROM routes ORDER BY route_no");
             ResultSet rows = statement.executeQuery()) {
            while (rows.next()) {
                headers.add(new String[] {rows.getString("route_no"), rows.getString("name")});
            }
        } catch (SQLException e) {
            throw Sql.failure("routes", e);
        }
        List<Route> found = new ArrayList<>();
        for (String[] header : headers) {
            found.add(route(header[0], header[1]));
        }
        return found;
    }

    private Route route(String routeNo, String name) {
        try (PreparedStatement statement = db.connection().prepareStatement(
                "SELECT stop_id, sequence_no, distance_km FROM route_stops WHERE route_no = ? ORDER BY sequence_no")) {
            statement.setString(1, routeNo);
            try (ResultSet rows = statement.executeQuery()) {
                List<RouteStop> stops = new ArrayList<>();
                while (rows.next()) {
                    stops.add(new RouteStop(rows.getString("stop_id"), rows.getInt("sequence_no"),
                            rows.getDouble("distance_km")));
                }
                return new Route(routeNo, name, stops);
            }
        } catch (SQLException e) {
            throw Sql.failure("route_stops", e);
        }
    }

    private static Stop stop(ResultSet row) throws SQLException {
        return new Stop(row.getString("stop_id"), row.getString("name"), row.getDouble("latitude"),
                row.getDouble("longitude"));
    }
}
