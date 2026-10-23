package lk.metrolink.journey.repository.jdbc;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import lk.metrolink.journey.domain.Account;
import lk.metrolink.journey.domain.FareRule;
import lk.metrolink.journey.domain.Passenger;
import lk.metrolink.journey.domain.QRTicket;
import lk.metrolink.journey.domain.Route;
import lk.metrolink.journey.domain.RouteStop;
import lk.metrolink.journey.domain.Stop;
import lk.metrolink.journey.repository.AccountRepository;
import lk.metrolink.journey.repository.CredentialRepository;
import lk.metrolink.journey.repository.DataAccessException;
import lk.metrolink.journey.repository.DemoData;
import lk.metrolink.journey.repository.FareRuleRepository;
import lk.metrolink.journey.repository.FareTransactionRepository;
import lk.metrolink.journey.repository.JourneyRepository;
import lk.metrolink.journey.repository.PassengerRepository;
import lk.metrolink.journey.repository.RouteRepository;
import lk.metrolink.journey.repository.Store;
import lk.metrolink.journey.repository.TicketRepository;
import lk.metrolink.journey.repository.UnitOfWork;

/**
 * The store that keeps everything in a SQL database through JDBC.
 *
 * <p>{@link #open} creates the tables if they are missing and, the first time, loads the network and the demo data.
 * After that the data stays in the database between runs, so customers who registered are still there after a
 * restart. "Start over with demo data" ({@link #reset}) empties every table and loads the demo data again.
 */
public final class JdbcStore implements Store {
    private static final String SCHEMA = "schema.sql";

    /** Children before parents, so no row is deleted while another still points at it. */
    private static final List<String> TABLES_CHILDREN_FIRST = List.of(
            "fare_transactions", "journeys", "tickets", "credentials", "accounts", "passengers",
            "fare_rules", "route_stops", "routes", "stops", "id_counters");

    private final JdbcDatabase db;
    private final RouteRepository routes;
    private final FareRuleRepository fareRules;
    private final PassengerRepository passengers;
    private final AccountRepository accounts;
    private final CredentialRepository credentials;
    private final TicketRepository tickets;
    private final JourneyRepository journeys;
    private final FareTransactionRepository fareTransactions;

    private JdbcStore(JdbcDatabase db) {
        this.db = db;
        this.routes = new JdbcRouteRepository(db);
        this.fareRules = new JdbcFareRuleRepository(db);
        this.passengers = new JdbcPassengerRepository(db);
        this.accounts = new JdbcAccountRepository(db);
        this.credentials = new JdbcCredentialRepository(db);
        this.tickets = new JdbcTicketRepository(db);
        this.journeys = new JdbcJourneyRepository(db);
        this.fareTransactions = new JdbcFareTransactionRepository(db);
    }

    /**
     * Opens the database at {@code url}, creates any missing tables, and loads the demo data (with tickets for
     * {@code today}) if the database is new. An existing database is left exactly as it is.
     */
    public static JdbcStore open(String url, String user, String password, LocalDate today) {
        JdbcDatabase database = new JdbcDatabase(url, user, password);
        try {
            JdbcStore store = new JdbcStore(database);
            store.createTables();
            if (store.isEmpty()) {
                store.reset(today);
            }
            return store;
        } catch (RuntimeException | Error e) {
            database.close();
            throw e;
        }
    }

    @Override public UnitOfWork unitOfWork() { return db; }
    @Override public RouteRepository routes() { return routes; }
    @Override public FareRuleRepository fareRules() { return fareRules; }
    @Override public PassengerRepository passengers() { return passengers; }
    @Override public AccountRepository accounts() { return accounts; }
    @Override public CredentialRepository credentials() { return credentials; }
    @Override public TicketRepository tickets() { return tickets; }
    @Override public JourneyRepository journeys() { return journeys; }
    @Override public FareTransactionRepository fareTransactions() { return fareTransactions; }

    @Override
    public void close() {
        db.close();
    }

    // ---- tables

    private void createTables() {
        List<String> statements = schemaStatements();
        db.execute(() -> {
            try (Statement statement = db.connection().createStatement()) {
                for (String sql : statements) {
                    statement.execute(sql);
                }
            } catch (SQLException e) {
                throw Sql.failure("schema", e);
            }
        });
    }

    /** The statements of {@code schema.sql}, without comments. */
    private static List<String> schemaStatements() {
        try (InputStream in = JdbcStore.class.getResourceAsStream(SCHEMA)) {
            if (in == null) {
                throw new DataAccessException("Missing " + SCHEMA + " on the class path", null);
            }
            StringBuilder script = new StringBuilder();
            for (String line : new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\n")) {
                if (!line.trim().startsWith("--")) {
                    script.append(line).append('\n');
                }
            }
            List<String> statements = new ArrayList<>();
            for (String part : script.toString().split(";")) {
                if (!part.isBlank()) {
                    statements.add(part.trim());
                }
            }
            return statements;
        } catch (IOException e) {
            throw new DataAccessException("Cannot read " + SCHEMA, e);
        }
    }

    private boolean isEmpty() {
        return db.run(() -> {
            try (PreparedStatement statement = db.connection().prepareStatement("SELECT COUNT(*) FROM stops");
                 ResultSet row = statement.executeQuery()) {
                row.next();
                return row.getLong(1) == 0;
            } catch (SQLException e) {
                throw Sql.failure("stops", e);
            }
        });
    }

    // ---- demo data

    /** Empties every table and loads the shared demo data (see {@link DemoData}), all in one transaction. */
    @Override
    public void reset(LocalDate today) {
        db.execute(() -> {
            emptyEveryTable();
            loadNetwork();
            loadCustomersAndTickets(today);
        });
    }

    private void emptyEveryTable() {
        try (Statement statement = db.connection().createStatement()) {
            for (String table : TABLES_CHILDREN_FIRST) {
                statement.executeUpdate("DELETE FROM " + table);
            }
        } catch (SQLException e) {
            throw Sql.failure("reset", e);
        }
    }

    /** Stops, routes and fare rules have no repository that writes, because Journey Execution only reads them. */
    private void loadNetwork() {
        Connection connection = db.connection();
        try {
            try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO stops (stop_id, name, latitude, longitude) VALUES (?, ?, ?, ?)")) {
                for (Stop stop : DemoData.stops()) {
                    insert.setString(1, stop.getStopId());
                    insert.setString(2, stop.getName());
                    insert.setDouble(3, stop.getLatitude());
                    insert.setDouble(4, stop.getLongitude());
                    insert.executeUpdate();
                }
            }
            for (Route route : DemoData.routes()) {
                try (PreparedStatement insert = connection.prepareStatement(
                        "INSERT INTO routes (route_no, name) VALUES (?, ?)")) {
                    insert.setString(1, route.getRouteNo());
                    insert.setString(2, route.getName());
                    insert.executeUpdate();
                }
                try (PreparedStatement insert = connection.prepareStatement(
                        "INSERT INTO route_stops (route_no, sequence_no, stop_id, distance_km) VALUES (?, ?, ?, ?)")) {
                    for (RouteStop stop : route.getStops()) {
                        insert.setString(1, route.getRouteNo());
                        insert.setInt(2, stop.getSequenceNo());
                        insert.setString(3, stop.getStopId());
                        insert.setDouble(4, stop.getDistanceKm());
                        insert.executeUpdate();
                    }
                }
            }
            try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO fare_rules (fare_rule_id, route_no, fare_type, base_fare, rate_per_km, flat_amount, "
                            + "effective_from, effective_to) VALUES (?, ?, ?, ?, ?, ?, ?, ?)")) {
                for (FareRule rule : DemoData.fareRules()) {
                    insert.setLong(1, rule.getFareRuleId());
                    insert.setString(2, rule.getRouteNo());
                    insert.setString(3, rule.getFareType().name());
                    insert.setLong(4, rule.getBaseFare());
                    insert.setDouble(5, rule.getRatePerKm());
                    insert.setLong(6, rule.getFlatAmount());
                    Sql.setDate(insert, 7, rule.getEffectiveFrom());
                    Sql.setDate(insert, 8, rule.getEffectiveTo());
                    insert.executeUpdate();
                }
            }
        } catch (SQLException e) {
            throw Sql.failure("network", e);
        }
    }

    /**
     * Goes through the same repositories the program uses, so ids come from the counters: customers and accounts 1 to
     * 5, tickets 1 to 10, in the order of {@link DemoData}. The next customer to register is therefore number 6.
     */
    private void loadCustomersAndTickets(LocalDate today) {
        for (DemoData.Customer customer : DemoData.customers(today)) {
            Passenger passenger = passengers.insert(customer.passenger());
            credentials.insert(passenger.getPassengerId(), customer.passwordHash());
            Account account = passenger.openAccount(0);
            account.credit(customer.balance(), customer.fundedAt());
            accounts.insert(account);
        }
        for (QRTicket ticket : DemoData.tickets(today)) {
            tickets.insert(ticket);
        }
    }
}
