package lk.metrolink.journey.web;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import lk.metrolink.journey.domain.Dates;

/**
 * Reads the fields of a JSON request body and collects every problem, so the client hears about all of them at
 * once. Call {@link #check()} before using any value: after a problem the getters return placeholders.
 */
final class Fields {
    private final JsonNode object;
    private final List<ValidationException.Issue> issues = new ArrayList<>();

    private Fields(JsonNode object) {
        this.object = object;
    }

    static Fields of(JsonNode body) {
        Fields fields = new Fields(body);
        if (body == null || !body.isObject()) {
            fields.issues.add(new ValidationException.Issue("", "Invalid input: expected an object"));
        }
        return fields;
    }

    private JsonNode field(String name) {
        return object != null && object.isObject() ? object.get(name) : null;
    }

    private boolean bodyIsObject() {
        return object != null && object.isObject();
    }

    private void fail(String name, String message) {
        issues.add(new ValidationException.Issue(name, message));
    }

    /** A required, non-empty string. */
    String text(String name) {
        JsonNode node = field(name);
        if (node == null || node.isNull()) {
            if (bodyIsObject()) {
                fail(name, "Invalid input: expected string, received undefined");
            }
            return "";
        }
        if (!node.isTextual()) {
            fail(name, "Invalid input: expected string");
            return "";
        }
        String value = node.asText().trim();
        if (value.isEmpty()) {
            fail(name, "Too small: expected string to have at least 1 character");
        }
        return value;
    }

    /** A required whole number from 1 to {@code max}. */
    long positiveInt(String name, long max) {
        JsonNode node = field(name);
        if (node == null || node.isNull()) {
            if (bodyIsObject()) {
                fail(name, "Invalid input: expected number, received undefined");
            }
            return 0;
        }
        if (!node.isNumber()) {
            fail(name, "Invalid input: expected number");
            return 0;
        }
        double number = node.doubleValue();
        if (number != Math.rint(number)) {
            fail(name, "Invalid input: expected a whole number");
            return 0;
        }
        if (number <= 0) {
            fail(name, "Too small: expected number to be greater than 0");
            return 0;
        }
        if (number > max) {
            fail(name, "Too big: expected number to be less than or equal to " + max);
            return 0;
        }
        return node.asLong();
    }

    /** A required string equal to the name of one of the constants. */
    <E extends Enum<E>> E enumValue(String name, Class<E> type) {
        JsonNode node = field(name);
        if (node != null && node.isTextual()) {
            for (E constant : type.getEnumConstants()) {
                if (constant.name().equals(node.asText())) {
                    return constant;
                }
            }
        }
        if (node != null || bodyIsObject()) {
            fail(name, "Invalid option: expected one of " + options(type));
        }
        return null;
    }

    /** An optional ISO-8601 moment with an offset, such as 2026-10-21T09:30:00+05:30. Null when absent. */
    Instant optionalInstant(String name) {
        JsonNode node = field(name);
        if (node == null || node.isNull()) {
            return null;
        }
        if (!node.isTextual()) {
            fail(name, "Invalid input: expected string");
            return null;
        }
        try {
            return Dates.parseInstant(node.asText());
        } catch (DateTimeParseException e) {
            fail(name, "Must be an ISO 8601 date-time with an offset, such as 2026-10-21T09:30:00+05:30");
            return null;
        }
    }

    void check() {
        if (!issues.isEmpty()) {
            throw new ValidationException(issues);
        }
    }

    private static <E extends Enum<E>> String options(Class<E> type) {
        List<String> names = new ArrayList<>();
        for (E constant : type.getEnumConstants()) {
            names.add("\"" + constant.name() + "\"");
        }
        return String.join("|", names);
    }
}
