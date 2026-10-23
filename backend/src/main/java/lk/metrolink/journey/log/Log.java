package lk.metrolink.journey.log;

import java.io.PrintStream;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Optional;

/**
 * The console log: one line for each thing worth seeing, so you can watch the program work.
 *
 * <p>It is plain Java (no logging library) and it does nothing until the application turns it on, so the tests stay
 * quiet. One line looks like this:
 *
 * <pre>
 *   22:41:07.123 INFO  #12   STATE    Journey #1: IN_PROGRESS -> COMPLETED (CompletedState)
 *   time         level request topic  message
 * </pre>
 *
 * <ul>
 *   <li><b>Level</b> says how much is shown. {@code OFF} shows nothing, {@code WARN} only what should be noticed (a
 *       state change that is not allowed, a failed login, an unexpected error), {@code INFO} the behaviour of the
 *       classes (state changes, fares, services, requests) and {@code DEBUG} everything, including every repository
 *       call and every read. See {@link Level}.</li>
 *   <li><b>Request</b> is the number of the HTTP request that caused the line, or {@code -} when no request did (for
 *       example at start-up). Lines of requests that overlap can be told apart by it.</li>
 *   <li><b>Topic</b> says which part of the design wrote the line: {@link #WEB}, {@link #UOW} (Unit of Work),
 *       {@link #SERVICE}, {@link #ACCOUNT}, {@link #STATE} (State pattern), {@link #FACTORY},
 *       {@link #STRATEGY} (Strategy pattern), {@link #REPO} (repositories) or {@link #APP}.</li>
 * </ul>
 *
 * <p>Three promises, each with a test:
 * <ul>
 *   <li>Logging never changes what the program does. A message that cannot be formatted is replaced by a note, and
 *       nothing is formatted at all when its level is off.</li>
 *   <li>A line is always one line of plain ASCII. Anything a client sent, such as a QR code text with a line break in
 *       it, is cleaned before it is printed, so a request cannot forge a log line, and a long line is cut.</li>
 *   <li>Callers never pass passwords, password hashes or whole email addresses. See {@code RepositoryTrace}, which
 *       hides them where the code could otherwise leak them.</li>
 * </ul>
 */
public final class Log {

    /** How much to show. A level shows its own lines and those of every level before it. */
    public enum Level {
        OFF(0), WARN(1), INFO(2), DEBUG(3);

        private final int rank;

        Level(int rank) {
            this.rank = rank;
        }

        /** The level written as text, in any case and with spaces around it. Empty if it is not one of the four. */
        public static Optional<Level> parse(String text) {
            if (text == null) {
                return Optional.empty();
            }
            String wanted = text.trim().toUpperCase(Locale.ROOT);
            for (Level level : values()) {
                if (level.name().equals(wanted)) {
                    return Optional.of(level);
                }
            }
            return Optional.empty();
        }
    }

    /** Start-up and settings. */
    public static final String APP = "APP";
    /** The HTTP front door: each request and its status. */
    public static final String WEB = "WEB";
    /** The Unit of Work: begin, commit, roll back. */
    public static final String UOW = "UOW";
    /** The services: what each one decided. */
    public static final String SERVICE = "SERVICE";
    /** Accounts: whether one can pay, and money moving in or out of one. */
    public static final String ACCOUNT = "ACCOUNT";
    /** The State pattern: a ticket or journey moving from one state to the next. */
    public static final String STATE = "STATE";
    /** Factories: how an account, a ticket or a journey comes to exist. */
    public static final String FACTORY = "FACTORY";
    /** The Strategy pattern: which fare strategy priced a trip. */
    public static final String STRATEGY = "STRATEGY";
    /** Repository calls, shown at DEBUG only. */
    public static final String REPO = "REPO";

    private static final ZoneId COLOMBO = ZoneId.of("Asia/Colombo");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");
    private static final int MAX_MESSAGE = 400;

    /** What one request tells every line written while it runs. */
    private static final class Request {
        private final long id;
        private final boolean routine;

        private Request(long id, boolean routine) {
            this.id = id;
            this.routine = routine;
        }
    }

    private static volatile Level threshold = Level.OFF;
    private static volatile PrintStream sink = System.out;
    private static final ThreadLocal<Request> REQUEST = new ThreadLocal<>();

    private Log() {
    }

    /** Turns logging on at {@code level}, writing to {@code out}. The application does this once, at start-up. */
    public static void configure(Level level, PrintStream out) {
        sink = out;
        threshold = level;
    }

    /** Back to the quiet default: off, and writing to the console. Tests call this when they are done. */
    public static void reset() {
        configure(Level.OFF, System.out);
    }

    public static Level level() {
        return threshold;
    }

    /**
     * Marks the start of an HTTP request on this thread. Every line written until {@link #endRequest()} carries its
     * number. A <i>routine</i> request (a read, or a fare quote the screen asks for as you choose stops) shows its
     * INFO lines only at DEBUG, so the screen reloading its lists does not bury the requests that change something.
     */
    public static void beginRequest(long id, boolean routine) {
        REQUEST.set(new Request(id, routine));
    }

    public static void endRequest() {
        REQUEST.remove();
    }

    /** True if a line at {@code level} would be shown right now. Use it to skip work that only a log line needs. */
    public static boolean enabled(Level level) {
        return effective(level).rank <= threshold.rank;
    }

    public static void warn(String topic, String format, Object... args) {
        write(Level.WARN, topic, format, args);
    }

    public static void info(String topic, String format, Object... args) {
        write(Level.INFO, topic, format, args);
    }

    public static void debug(String topic, String format, Object... args) {
        write(Level.DEBUG, topic, format, args);
    }

    /** The level a line is judged at: INFO lines of a routine request count as DEBUG. Warnings always count. */
    private static Level effective(Level level) {
        Request request = REQUEST.get();
        return level == Level.INFO && request != null && request.routine ? Level.DEBUG : level;
    }

    private static void write(Level level, String topic, String format, Object[] args) {
        if (!enabled(level)) {
            return;                                           // nothing is formatted when the line is not shown
        }
        String message;
        try {
            message = args.length == 0 ? format : String.format(Locale.ROOT, format, args);
        } catch (RuntimeException e) {
            message = "(this log message could not be formatted: " + format + ")";
        }
        Request request = REQUEST.get();
        String line = String.format(Locale.ROOT, "%s %-5s %-5s %-8s %s",
                TIME.format(ZonedDateTime.now(COLOMBO)),
                level.name(),
                request == null ? "-" : "#" + request.id,
                topic,
                clean(message));
        sink.println(line);
    }

    /** One line of printable ASCII, cut to a sensible length. */
    private static String clean(String message) {
        StringBuilder out = new StringBuilder(Math.min(message.length(), MAX_MESSAGE));
        for (int i = 0; i < message.length() && out.length() < MAX_MESSAGE; i++) {
            char c = message.charAt(i);
            out.append(c >= 0x20 && c <= 0x7e ? c : '?');
        }
        if (message.length() > MAX_MESSAGE) {
            out.setLength(MAX_MESSAGE - 3);
            out.append("...");
        }
        return out.toString();
    }
}
