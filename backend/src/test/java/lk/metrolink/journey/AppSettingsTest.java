package lk.metrolink.journey;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** The default database settings carry the two options the project depends on. */
public class AppSettingsTest {

    @Test
    public void theDefaultDatabaseIsAnH2FileInTheDataFolder() {
        assertTrue(App.DEFAULT_DB, App.DEFAULT_DB.startsWith("jdbc:h2:file:./data/"));
    }

    @Test
    public void theDefaultDatabaseWritesEveryCommitToDiskBeforeAnsweringSoAKillCannotLoseARegistration() {
        assertTrue(App.DEFAULT_DB, App.DEFAULT_DB.contains(";WRITE_DELAY=0"));
    }

    @Test
    public void theDefaultDatabaseCanBeOpenedByAToolWhileTheServerRuns() {
        assertTrue(App.DEFAULT_DB, App.DEFAULT_DB.contains(";AUTO_SERVER=TRUE"));
    }
}
