package lk.metrolink.journey;

import java.io.IOException;
import java.time.LocalDate;
import lk.metrolink.journey.domain.Dates;
import lk.metrolink.journey.repository.DataAccessException;
import lk.metrolink.journey.repository.Store;
import lk.metrolink.journey.repository.jdbc.JdbcStore;
import lk.metrolink.journey.repository.memory.InMemoryStore;
import lk.metrolink.journey.web.ApiServer;

/**
 * Starts the MetroLink API. Settings come from environment variables:
 *
 * <ul>
 *   <li>{@code PORT}: the port to listen on. Default 4000.</li>
 *   <li>{@code METROLINK_DB}: where the data lives. A JDBC URL, or {@code memory} to keep everything in memory and
 *       forget it on exit. Default: an H2 database file at {@code ./data/metrolink} (see {@link #DEFAULT_DB}).</li>
 *   <li>{@code METROLINK_DB_USER} and {@code METROLINK_DB_PASSWORD}: the database login. Default {@code sa} and an
 *       empty password, which is what a new H2 file database starts with.</li>
 * </ul>
 */
public final class App {

    /**
     * An H2 file database next to where the server is started.
     *
     * <ul>
     *   <li>{@code AUTO_SERVER=TRUE} lets a tool such as DBeaver open the same file while the server runs.</li>
     *   <li>{@code WRITE_DELAY=0} writes every commit to disk before it returns. Without it H2 waits up to a second, so
     *       a customer who has just been told "account opened" can be lost if the program is killed (an IDE's stop
     *       button, or Task Manager) rather than stopped normally. The cost, a disk write per change, is nothing at
     *       this size.</li>
     * </ul>
     */
    static final String DEFAULT_DB = "jdbc:h2:file:./data/metrolink;AUTO_SERVER=TRUE;WRITE_DELAY=0";

    private App() {
    }

    public static void main(String[] args) throws IOException {
        int port = 4000;
        String fromEnvironment = System.getenv("PORT");
        if (fromEnvironment != null && !fromEnvironment.isBlank()) {
            try {
                port = Integer.parseInt(fromEnvironment.trim());
            } catch (NumberFormatException e) {
                System.err.println("PORT must be a number, but it was \"" + fromEnvironment + "\"");
                System.exit(2);
            }
        }

        LocalDate today = Dates.today();
        String database = setting("METROLINK_DB", DEFAULT_DB);
        Store store = null;
        try {
            store = openStore(database, today);
        } catch (DataAccessException e) {
            System.err.println(e.getMessage());
            System.exit(2);
        }

        Container container = Container.create(store, today);
        ApiServer server = ApiServer.start(container, port);
        Store openStore = store;
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            server.stop();
            openStore.close();
        }, "shutdown"));
        System.out.println("MetroLink API listening on http://localhost:" + server.port() + "/api");
        System.out.println("Data: " + (isMemory(database) ? "in memory (forgotten on exit)" : database));
    }

    private static Store openStore(String database, LocalDate today) {
        if (isMemory(database)) {
            return InMemoryStore.seeded(today);
        }
        return JdbcStore.open(database, setting("METROLINK_DB_USER", "sa"), setting("METROLINK_DB_PASSWORD", ""), today);
    }

    private static boolean isMemory(String database) {
        return database.equalsIgnoreCase("memory");
    }

    private static String setting(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
