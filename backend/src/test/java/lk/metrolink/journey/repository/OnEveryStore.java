package lk.metrolink.journey.repository;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.After;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/**
 * Base class for tests that must pass on every store. JUnit runs each test method once per {@link StoreKind}, and the
 * test name shows which one, for example {@code boardsAValidTicket[H2]}.
 *
 * <p>Stores opened through {@link #open} are closed after each test.
 */
@RunWith(Parameterized.class)
public abstract class OnEveryStore {

    @Parameterized.Parameters(name = "{0}")
    public static Object[] stores() {
        return StoreKind.values();
    }

    @Parameterized.Parameter
    public StoreKind kind;

    private final List<Store> opened = new ArrayList<>();

    /** A store of the kind under test, with demo data for {@code today}. */
    protected Store open(LocalDate today) {
        Store store = kind.open(today);
        opened.add(store);
        return store;
    }

    @After
    public void closeStores() {
        for (Store store : opened) {
            store.close();
        }
        opened.clear();
    }
}
