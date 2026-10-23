package lk.metrolink.journey.repository.jdbc;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import lk.metrolink.journey.domain.FareRule;
import lk.metrolink.journey.repository.FareRuleRepository;

/** Fare rules for a route, oldest first. Read only, like the rest of the network. */
public final class JdbcFareRuleRepository implements FareRuleRepository {
    private final JdbcDatabase db;

    public JdbcFareRuleRepository(JdbcDatabase db) {
        this.db = db;
    }

    @Override
    public List<FareRule> listForRoute(String routeNo) {
        try (PreparedStatement statement = db.connection().prepareStatement(
                "SELECT fare_rule_id, fare_type, base_fare, rate_per_km, flat_amount, effective_from, effective_to "
                        + "FROM fare_rules WHERE route_no = ? ORDER BY effective_from, fare_rule_id")) {
            statement.setString(1, routeNo);
            try (ResultSet rows = statement.executeQuery()) {
                List<FareRule> found = new ArrayList<>();
                while (rows.next()) {
                    long id = rows.getLong("fare_rule_id");
                    LocalDate from = Sql.getDate(rows, "effective_from");
                    LocalDate to = Sql.getDate(rows, "effective_to");
                    found.add("FLAT".equals(rows.getString("fare_type"))
                            ? FareRule.flat(id, routeNo, rows.getLong("flat_amount"), from, to)
                            : FareRule.distance(id, routeNo, rows.getLong("base_fare"), rows.getDouble("rate_per_km"),
                                    from, to));
                }
                return found;
            }
        } catch (SQLException e) {
            throw Sql.failure("fare_rules", e);
        }
    }
}
