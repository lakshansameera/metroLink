package lk.metrolink.journey.domain;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * What a new customer typed on the registration form, and the rules about it (O1 and O3).
 *
 * <p>The request knows whether it is acceptable. {@link #problems(LocalDate)} lists everything that is wrong, so the
 * customer hears about all of it at once instead of fixing one field per attempt. Only when the list is empty may the
 * other getters be used.
 */
public final class OnboardingRequest {

    public static final int NAME_MAX = 100;
    public static final int EMAIL_MAX = 254;
    public static final int PASSWORD_MIN = 8;
    public static final int PASSWORD_MAX = 128;

    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    /** One wrong field and what is wrong with it. */
    public static final class Problem {
        private final String field;
        private final String message;

        public Problem(String field, String message) {
            this.field = field;
            this.message = message;
        }

        public String getField() { return field; }
        public String getMessage() { return message; }
    }

    private final String name;
    private final String email;
    private final String password;
    private final ResidencyKind residencyKind;
    private final LocalDate visaExpiry;

    /** Any value may be null; {@link #problems(LocalDate)} reports what is missing. */
    public OnboardingRequest(String name, String email, String password, ResidencyKind residencyKind,
                             LocalDate visaExpiry) {
        this.name = name == null ? null : name.trim();
        this.email = email == null ? null : email.trim().toLowerCase(Locale.ROOT);
        this.password = password;
        this.residencyKind = residencyKind;
        this.visaExpiry = visaExpiry;
    }

    /** Everything wrong with this request, judged on {@code today} (the Colombo date). Empty means acceptable. */
    public List<Problem> problems(LocalDate today) {
        List<Problem> found = new ArrayList<>();

        if (name == null || name.isEmpty()) {
            found.add(new Problem("name", "Name is required"));
        } else if (name.length() > NAME_MAX) {
            found.add(new Problem("name", "Name must be at most " + NAME_MAX + " characters"));
        }

        if (email == null || email.isEmpty()) {
            found.add(new Problem("email", "Email is required"));
        } else if (email.length() > EMAIL_MAX) {
            found.add(new Problem("email", "Email must be at most " + EMAIL_MAX + " characters"));
        } else if (!EMAIL.matcher(email).matches()) {
            found.add(new Problem("email", "Email does not look like an address"));
        }

        if (password == null || password.isEmpty()) {
            found.add(new Problem("password", "Password is required"));
        } else if (password.length() < PASSWORD_MIN) {
            found.add(new Problem("password", "Password must be at least " + PASSWORD_MIN + " characters"));
        } else if (password.length() > PASSWORD_MAX) {
            found.add(new Problem("password", "Password must be at most " + PASSWORD_MAX + " characters"));
        }

        if (residencyKind == null) {
            found.add(new Problem("residency", "Residency is required"));
        } else if (residencyKind == ResidencyKind.NON_RESIDENT) {
            if (visaExpiry == null) {
                found.add(new Problem("visaExpiry", "Visa expiry date is required for a non-resident"));
            } else if (visaExpiry.isBefore(today)) {
                found.add(new Problem("visaExpiry", "Visa expiry date must be today or later"));
            }
        } else if (visaExpiry != null) {
            found.add(new Problem("visaExpiry", "Visa expiry date is only for non-residents"));
        }
        return found;
    }

    public String getName() { return name; }

    /** The email as it is stored and compared: trimmed and in lower case (rule O2). */
    public String getEmail() { return email; }

    public String getPassword() { return password; }

    /**
     * Turns the answer into the object that knows which account to open. This is a simple factory: the caller never
     * writes {@code if (resident)}. Call it only after {@link #problems(LocalDate)} came back empty.
     */
    public Residency residency() {
        return residencyKind == ResidencyKind.NON_RESIDENT ? new NonResident(visaExpiry) : new Resident();
    }
}
