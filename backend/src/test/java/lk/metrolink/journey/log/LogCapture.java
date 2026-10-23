package lk.metrolink.journey.log;

import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Turns the console log on at a chosen level and keeps what it writes, so a test can read it. Always use it in a
 * try-with-resources block: closing it puts the log back to its quiet default for the next test.
 */
public final class LogCapture implements AutoCloseable {

    private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();

    public LogCapture(Log.Level level) {
        Log.configure(level, new PrintStream(bytes, true, StandardCharsets.UTF_8));
    }

    /** Every line written so far. */
    public List<String> lines() {
        String text = text();
        return text.isEmpty() ? List.of() : Arrays.asList(text.split("\r?\n"));
    }

    /** All of it as one piece of text, for "nothing like this was ever printed" checks. */
    public String text() {
        return new String(bytes.toByteArray(), StandardCharsets.UTF_8);
    }

    /** The lines written under one topic, such as {@code STATE}. */
    public List<String> lines(String topic) {
        List<String> found = new ArrayList<>();
        for (String line : lines()) {
            if (line.length() > 25 && line.substring(25).startsWith(topic + " ")) {
                found.add(line);
            }
        }
        return found;
    }

    /** Fails unless each fragment appears in a line, each in a later line than the one before. */
    public void assertInOrder(String... fragments) {
        List<String> lines = lines();
        int from = 0;
        for (String fragment : fragments) {
            int found = -1;
            for (int i = from; i < lines.size(); i++) {
                if (lines.get(i).contains(fragment)) {
                    found = i;
                    break;
                }
            }
            assertTrue("expected a line with \"" + fragment + "\" after line " + from + " in:\n" + text(), found >= 0);
            from = found + 1;
        }
    }

    @Override
    public void close() {
        Log.reset();
    }
}
