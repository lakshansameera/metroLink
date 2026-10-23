package lk.metrolink.journey.log;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.time.Duration;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.After;
import org.junit.Test;

/** The logger itself: what it shows at each level, how a line looks, and what it refuses to print (rules L1 to L5). */
public class LogTest {

    private static final String LINE = "\\d\\d:\\d\\d:\\d\\d\\.\\d{3} (WARN |INFO |DEBUG) (-    |#\\d+ *) [A-Z]+ +[ -~]*";

    @After
    public void quiet() {
        Log.reset();
        Log.endRequest();
    }

    private static void writeOneOfEach() {
        Log.warn(Log.STATE, "a warning");
        Log.info(Log.STATE, "some information");
        Log.debug(Log.STATE, "a detail");
    }

    @Test
    public void nothingIsPrintedUntilTheApplicationTurnsTheLogOn() {                          // L1
        assertEquals(Log.Level.OFF, Log.level());
        try (LogCapture capture = new LogCapture(Log.Level.OFF)) {
            writeOneOfEach();
            assertEquals(List.of(), capture.lines());
        }
    }

    @Test
    public void resetReturnsToOffEvenAfterItWasOn() {                                         // L1
        LogCapture capture = new LogCapture(Log.Level.DEBUG);
        Log.info(Log.APP, "seen");
        capture.close();

        Log.info(Log.APP, "not seen");

        assertEquals(Log.Level.OFF, Log.level());
        assertEquals(1, capture.lines().size());
    }

    @Test
    public void eachLevelShowsItsOwnLinesAndThoseOfTheLevelsBeforeIt() {                      // L2
        try (LogCapture capture = new LogCapture(Log.Level.WARN)) {
            writeOneOfEach();
            assertEquals(1, capture.lines().size());
            assertTrue(capture.text().contains("a warning"));
        }
        try (LogCapture capture = new LogCapture(Log.Level.INFO)) {
            writeOneOfEach();
            assertEquals(2, capture.lines().size());
            assertFalse(capture.text().contains("a detail"));
        }
        try (LogCapture capture = new LogCapture(Log.Level.DEBUG)) {
            writeOneOfEach();
            assertEquals(3, capture.lines().size());
        }
    }

    @Test
    public void theLevelsCanBeWrittenInAnyCase() {                                            // L2
        assertEquals(Optional.of(Log.Level.OFF), Log.Level.parse("off"));
        assertEquals(Optional.of(Log.Level.WARN), Log.Level.parse("Warn"));
        assertEquals(Optional.of(Log.Level.INFO), Log.Level.parse("INFO"));
        assertEquals(Optional.of(Log.Level.DEBUG), Log.Level.parse("  debug  "));
    }

    @Test
    public void anythingElseIsNotALevel() {                                                   // L2
        assertEquals(Optional.empty(), Log.Level.parse("verbose"));
        assertEquals(Optional.empty(), Log.Level.parse(""));
        assertEquals(Optional.empty(), Log.Level.parse(null));
    }

    @Test
    public void aLineHasTimeLevelRequestTopicAndMessageOnOneLine() {                          // L3
        try (LogCapture capture = new LogCapture(Log.Level.DEBUG)) {
            Log.info(Log.STATE, "Journey #1: IN_PROGRESS -> COMPLETED");

            String line = capture.lines().get(0);
            assertTrue(line, line.matches("\\d\\d:\\d\\d:\\d\\d\\.\\d{3} INFO  -     STATE    Journey #1: IN_PROGRESS -> COMPLETED"));
        }
    }

    @Test
    public void theTimeIsTheTimeInColombo() {                                                 // L3
        try (LogCapture capture = new LogCapture(Log.Level.INFO)) {
            Log.info(Log.APP, "now");

            LocalTime logged = LocalTime.parse(capture.lines().get(0).substring(0, 12));
            long seconds = Math.abs(Duration.between(logged, LocalTime.now(ZoneId.of("Asia/Colombo"))).getSeconds());
            assertTrue("off by " + seconds + " s", seconds < 5 || seconds > 86_395);
        }
    }

    @Test
    public void everyTopicFitsItsColumnSoTheMessagesLineUp() {                                // L3
        try (LogCapture capture = new LogCapture(Log.Level.INFO)) {
            for (String topic : List.of(Log.APP, Log.WEB, Log.UOW, Log.SERVICE, Log.ACCOUNT, Log.STATE, Log.FACTORY,
                    Log.STRATEGY, Log.REPO)) {
                Log.info(topic, "x");
            }
            for (String line : capture.lines()) {
                assertEquals(line, 'x', line.charAt(line.length() - 1));
                assertEquals(line, 35, line.length());
            }
        }
    }

    @Test
    public void linesWrittenWhileARequestRunsCarryItsNumberAndOthersDoNot() {                 // L3
        try (LogCapture capture = new LogCapture(Log.Level.INFO)) {
            Log.info(Log.APP, "before");
            Log.beginRequest(12, false);
            Log.info(Log.WEB, "during");
            Log.endRequest();
            Log.info(Log.APP, "after");

            List<String> lines = capture.lines();
            assertTrue(lines.get(0), lines.get(0).contains(" -     APP "));
            assertTrue(lines.get(1), lines.get(1).contains(" #12   WEB "));
            assertTrue(lines.get(2), lines.get(2).contains(" -     APP "));
        }
    }

    @Test
    public void theRequestNumberBelongsToTheThreadThatHandlesIt() throws Exception {          // L3
        try (LogCapture capture = new LogCapture(Log.Level.INFO)) {
            Log.beginRequest(1, false);
            Thread other = new Thread(() -> Log.info(Log.APP, "from another thread"));
            other.start();
            other.join();

            assertTrue(capture.text(), capture.lines().get(0).contains(" -     APP "));
        }
    }

    @Test
    public void aRoutineRequestShowsItsInfoLinesOnlyAtDebug() {                               // L5
        try (LogCapture capture = new LogCapture(Log.Level.INFO)) {
            Log.beginRequest(1, true);
            Log.info(Log.WEB, "GET /api/passengers");
            assertEquals(List.of(), capture.lines());
            assertFalse(Log.enabled(Log.Level.INFO));
        }
        try (LogCapture capture = new LogCapture(Log.Level.DEBUG)) {
            Log.beginRequest(1, true);
            Log.info(Log.WEB, "GET /api/passengers");
            assertEquals(1, capture.lines().size());
            assertTrue(Log.enabled(Log.Level.INFO));
        }
    }

    @Test
    public void aWarningIsShownEvenInARoutineRequest() {                                      // L5
        try (LogCapture capture = new LogCapture(Log.Level.WARN)) {
            Log.beginRequest(1, true);
            Log.warn(Log.STATE, "something not allowed");

            assertEquals(1, capture.lines().size());
        }
    }

    @Test
    public void anOrdinaryRequestIsNotRoutine() {                                             // L5
        try (LogCapture capture = new LogCapture(Log.Level.INFO)) {
            Log.beginRequest(1, false);
            Log.info(Log.WEB, "POST /api/reader/scan");

            assertEquals(1, capture.lines().size());
        }
    }

    @Test
    public void whenALineIsNotShownItsArgumentsAreNeverTurnedIntoText() {                     // L8
        AtomicInteger asked = new AtomicInteger();
        Object expensive = new Object() {
            @Override
            public String toString() {
                asked.incrementAndGet();
                throw new IllegalStateException("should not be asked");
            }
        };

        Log.info(Log.STATE, "value %s", expensive);        // the log is off

        assertEquals(0, asked.get());
    }

    @Test
    public void aMessageThatCannotBeFormattedIsReplacedByANoteAndNothingIsThrown() {          // L8
        try (LogCapture capture = new LogCapture(Log.Level.DEBUG)) {
            Log.info(Log.STATE, "number %d", "not a number");
            Log.info(Log.STATE, "value %s", new Object() {
                @Override
                public String toString() {
                    throw new IllegalStateException("broken toString");
                }
            });

            assertEquals(2, capture.lines().size());
            assertTrue(capture.text(), capture.lines().get(0).contains("could not be formatted"));
            assertTrue(capture.text(), capture.lines().get(1).contains("could not be formatted"));
        }
    }

    @Test
    public void textFromAClientCannotStartANewLineOrForgeAnEntry() {                          // L3
        try (LogCapture capture = new LogCapture(Log.Level.INFO)) {
            Log.info(Log.SERVICE, "ticket %s", "X\n22:00:00.000 WARN  #1    STATE    forged\r\nmore");

            assertEquals(capture.text(), 1, capture.lines().size());
            assertTrue(capture.text(), capture.lines().get(0).endsWith("ticket X?22:00:00.000 WARN  #1    STATE    forged??more"));
        }
    }

    @Test
    public void everyLineIsPlainAsciiSoAWindowsConsoleShowsItCorrectly() {                    // L3
        try (LogCapture capture = new LogCapture(Log.Level.INFO)) {
            Log.info(Log.SERVICE, "ticket %s and %s and %s", "caf\u00e9", "\u0dc1\u0dca\u200d\u0dbb\u0dd3", "tab\there");

            String line = capture.lines().get(0);
            assertTrue(line, line.matches(LINE));
            assertTrue(line, line.endsWith("ticket caf? and ????? and tab?here"));
        }
    }

    @Test
    public void aVeryLongMessageIsCut() {                                                     // L3
        try (LogCapture capture = new LogCapture(Log.Level.INFO)) {
            Log.info(Log.SERVICE, "%s", "a".repeat(5_000));

            String line = capture.lines().get(0);
            assertEquals(34 + 400, line.length());
            assertTrue(line, line.endsWith("..."));
        }
    }

    @Test
    public void aMessageJustUnderTheLimitIsNotCut() {                                         // L3
        try (LogCapture capture = new LogCapture(Log.Level.INFO)) {
            Log.info(Log.SERVICE, "%s", "a".repeat(400));

            String line = capture.lines().get(0);
            assertTrue(line, line.endsWith("aaa"));
            assertFalse(line, line.endsWith("..."));
        }
    }

    @Test
    public void linesFromManyThreadsNeverRunIntoEachOther() throws Exception {                // L3
        try (LogCapture capture = new LogCapture(Log.Level.INFO)) {
            ExecutorService pool = Executors.newFixedThreadPool(16);
            List<Future<?>> done = new ArrayList<>();
            for (int t = 0; t < 16; t++) {
                int thread = t;
                done.add(pool.submit(() -> {
                    Log.beginRequest(thread, false);
                    for (int i = 0; i < 200; i++) {
                        Log.info(Log.SERVICE, "thread %d line %d with some text to make it longer", thread, i);
                    }
                    Log.endRequest();
                }));
            }
            for (Future<?> f : done) {
                f.get();
            }
            pool.shutdown();

            List<String> lines = capture.lines();
            assertEquals(3_200, lines.size());
            for (String line : lines) {
                assertTrue(line, line.matches(LINE));
            }
        }
    }
}
