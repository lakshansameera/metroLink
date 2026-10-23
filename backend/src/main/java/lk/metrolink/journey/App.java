package lk.metrolink.journey;

import java.io.IOException;
import lk.metrolink.journey.domain.Dates;
import lk.metrolink.journey.web.ApiServer;

/** Starts the Journey Execution API. It listens on port 4000 unless the PORT environment variable says otherwise. */
public final class App {

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

        Container container = Container.create(Dates.today());
        ApiServer server = ApiServer.start(container, port);
        Runtime.getRuntime().addShutdownHook(new Thread(server::stop, "shutdown"));
        System.out.println("MetroLink Journey Execution API listening on http://localhost:" + server.port() + "/api");
    }
}
