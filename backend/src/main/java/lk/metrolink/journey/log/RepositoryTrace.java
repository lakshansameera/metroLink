package lk.metrolink.journey.log;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.time.temporal.Temporal;
import java.util.Collection;
import java.util.Optional;
import lk.metrolink.journey.domain.Account;
import lk.metrolink.journey.domain.FareRule;
import lk.metrolink.journey.domain.FareTransaction;
import lk.metrolink.journey.domain.Journey;
import lk.metrolink.journey.domain.Passenger;
import lk.metrolink.journey.domain.PasswordHash;
import lk.metrolink.journey.domain.QRTicket;
import lk.metrolink.journey.domain.Route;
import lk.metrolink.journey.domain.Stop;

/**
 * Shows every repository call at DEBUG, without touching any of the sixteen repository classes.
 *
 * <p>This is the Proxy pattern, built with the JDK's own {@link Proxy}. {@code wrap} returns an object that has the
 * same interface as the repository, forwards each call to it, and writes one line about the call:
 *
 * <pre>
 *   REPO  TicketRepository.findByQrCode("MLK-HAPPY01") -> found ticket #1 MLK-HAPPY01 ACTIVE
 *   REPO  JourneyRepository.save(journey #1 IN_PROGRESS) -> done
 * </pre>
 *
 * <p>What the line may say is decided here, in one place, because a repository handles the most private data in the
 * program. Only a few kinds of value are described by what they are (a ticket, a journey, an account, a count);
 * anything else is shown as its class name and nothing more. A password hash is never described, and an email is
 * shortened to {@code n***@example.com}.
 *
 * <p>An exception from the repository is rethrown exactly as it was, so a constraint violation still reaches the
 * code that turns it into HTTP 409.
 */
public final class RepositoryTrace {

    private static final int LONGEST_TEXT = 60;

    private RepositoryTrace() {
    }

    /** {@code target} behind the same interface, with every call shown at DEBUG. */
    public static <T> T wrap(Class<T> type, T target) {
        InvocationHandler handler = (proxy, method, args) -> {
            if (method.getDeclaringClass() == Object.class) {
                return objectMethod(type, target, proxy, method.getName(), args);
            }
            if (!Log.enabled(Log.Level.DEBUG)) {
                return invoke(method, target, args);
            }
            String call = type.getSimpleName() + "." + method.getName() + "(" + describeAll(args) + ")";
            try {
                Object result = invoke(method, target, args);
                Log.debug(Log.REPO, "%s -> %s", call, method.getReturnType() == void.class ? "done" : describe(result));
                return result;
            } catch (Throwable failure) {
                Log.debug(Log.REPO, "%s -> failed: %s", call, describeFailure(failure));
                throw failure;
            }
        };
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, handler));
    }

    /** The proxy is its own object: equal only to itself, and printed as what it wraps. These calls are not logged. */
    private static Object objectMethod(Class<?> type, Object target, Object proxy, String name, Object[] args) {
        switch (name) {
            case "equals":
                return proxy == args[0];
            case "hashCode":
                return System.identityHashCode(proxy);
            default:
                return type.getSimpleName() + " with console trace, over " + target;
        }
    }

    private static Object invoke(java.lang.reflect.Method method, Object target, Object[] args) throws Throwable {
        try {
            return method.invoke(target, args);
        } catch (InvocationTargetException e) {
            throw e.getCause();                    // the repository's own exception, not the reflection wrapper
        }
    }

    private static String describeAll(Object[] args) {
        if (args == null || args.length == 0) {
            return "";
        }
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < args.length; i++) {
            out.append(i == 0 ? "" : ", ").append(describe(args[i]));
        }
        return out.toString();
    }

    /** A short, safe description of a failure: its kind and its message. */
    static String describeFailure(Throwable failure) {
        return failure.getClass().getSimpleName() + (failure.getMessage() == null ? "" : ": " + failure.getMessage());
    }

    /** What a value looks like in a log line. Anything not listed is shown as its class name only. */
    static String describe(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof Optional) {
            Optional<?> optional = (Optional<?>) value;
            return optional.isPresent() ? "found " + describe(optional.get()) : "nothing found";
        }
        if (value instanceof Collection) {
            return ((Collection<?>) value).size() + " rows";
        }
        if (value instanceof PasswordHash) {
            return "a password hash (hidden)";
        }
        if (value instanceof QRTicket) {
            QRTicket t = (QRTicket) value;
            return "ticket #" + t.getTicketId() + " " + t.getQrCode() + " " + t.status();
        }
        if (value instanceof Journey) {
            Journey j = (Journey) value;
            return "journey #" + j.getJourneyId() + " " + j.status();
        }
        if (value instanceof Account) {
            Account a = (Account) value;
            return "account #" + a.getAccountId() + " " + a.type() + " balance " + a.getBalance();
        }
        if (value instanceof Passenger) {
            Passenger p = (Passenger) value;
            return "passenger #" + p.getPassengerId() + " " + p.getResidency().name();
        }
        if (value instanceof FareTransaction) {
            FareTransaction f = (FareTransaction) value;
            return "transaction " + f.getReference() + " LKR " + f.getAmount();
        }
        if (value instanceof Route) {
            return "route " + ((Route) value).getRouteNo();
        }
        if (value instanceof Stop) {
            return "stop " + ((Stop) value).getStopId();
        }
        if (value instanceof FareRule) {
            return "fare rule #" + ((FareRule) value).getFareRuleId();
        }
        if (value instanceof String) {
            return "\"" + shorten(maskEmail((String) value)) + "\"";
        }
        if (value instanceof Number || value instanceof Boolean || value instanceof Enum || value instanceof Temporal) {
            return value.toString();
        }
        return value.getClass().getSimpleName();
    }

    /** {@code nimal@example.com} becomes {@code n***@example.com}. Text without an @ is left alone. */
    static String maskEmail(String text) {
        int at = text.indexOf('@');
        if (at < 1) {
            return text;
        }
        return text.charAt(0) + "***" + text.substring(at);
    }

    private static String shorten(String text) {
        return text.length() <= LONGEST_TEXT ? text : text.substring(0, LONGEST_TEXT) + "...";
    }
}
