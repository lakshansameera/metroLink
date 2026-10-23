package lk.metrolink.journey.repository;

import java.time.LocalDate;
import java.util.UUID;
import lk.metrolink.journey.repository.jdbc.JdbcStore;
import lk.metrolink.journey.repository.memory.InMemoryStore;

/**
 * The two ways the application can keep its data. Tests that depend on the store run once for each, so the in-memory
 * store and the SQL store are held to exactly the same behaviour.
 */
public enum StoreKind {

    /** Everything in memory. */
    MEMORY {
        @Override
        public Store open(LocalDate today) {
            return InMemoryStore.seeded(today);
        }
    },

    /** A real SQL database (H2) through JDBC. Each one is a new, private, in-memory database. */
    H2 {
        @Override
        public Store open(LocalDate today) {
            return JdbcStore.open("jdbc:h2:mem:test" + UUID.randomUUID().toString().replace("-", ""), "sa", "", today);
        }
    };

    /** A store holding the network and the demo data, with demo tickets dated for {@code today}. */
    public abstract Store open(LocalDate today);
}
