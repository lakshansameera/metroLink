package lk.metrolink.journey;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import lk.metrolink.journey.log.Log;
import org.junit.Test;

/** The default settings carry the options the project depends on: the database, and how much the console shows. */
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

    // ---- METROLINK_LOG (rule L2)

    @Test
    public void theConsoleShowsTheBehaviourOfTheClassesUnlessToldOtherwise() {
        assertEquals("info", App.DEFAULT_LOG);
        assertEquals(Log.Level.INFO, App.logLevel(App.DEFAULT_LOG));
    }

    @Test
    public void eachOfTheFourLevelsCanBeAskedForInAnyCase() {
        assertEquals(Log.Level.OFF, App.logLevel("off"));
        assertEquals(Log.Level.WARN, App.logLevel("warn"));
        assertEquals(Log.Level.INFO, App.logLevel("info"));
        assertEquals(Log.Level.DEBUG, App.logLevel("debug"));
        assertEquals(Log.Level.DEBUG, App.logLevel("  DEBUG "));
        assertEquals(Log.Level.WARN, App.logLevel("Warn"));
    }

    @Test
    public void aLevelThatIsNotOneOfTheFourStopsStartUpAndSaysWhichAreAllowed() {
        for (String wrong : new String[] {"", "verbose", "trace", "1", "inf", "debug,info"}) {
            try {
                App.logLevel(wrong);
                fail("\"" + wrong + "\" is not a level");
            } catch (IllegalArgumentException e) {
                assertEquals("METROLINK_LOG must be off, warn, info or debug, but it was \"" + wrong + "\"",
                        e.getMessage());
            }
        }
    }
}
