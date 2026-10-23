package lk.metrolink.journey.web;

import java.util.List;

/** A malformed request: reported as HTTP 400 with one entry for every field that was wrong. */
public class ValidationException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    /** One wrong field and what is wrong with it. */
    public static final class Issue {
        private final String path;
        private final String message;

        public Issue(String path, String message) {
            this.path = path;
            this.message = message;
        }

        public String getPath() { return path; }
        public String getMessage() { return message; }
    }

    private final transient List<Issue> issues;

    public ValidationException(List<Issue> issues) {
        super("Invalid request");
        this.issues = List.copyOf(issues);
    }

    public ValidationException(String path, String message) {
        this(List.of(new Issue(path, message)));
    }

    public List<Issue> issues() {
        return issues;
    }
}
