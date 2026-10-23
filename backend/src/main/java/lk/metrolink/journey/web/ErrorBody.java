package lk.metrolink.journey.web;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/** What every error response looks like: a short code, a sentence, and (for bad requests) the wrong fields. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class ErrorBody {
    private final String error;
    private final String message;
    private final List<ValidationException.Issue> details;

    public ErrorBody(String error, String message, List<ValidationException.Issue> details) {
        this.error = error;
        this.message = message;
        this.details = details;
    }

    public String getError() { return error; }
    public String getMessage() { return message; }
    public List<ValidationException.Issue> getDetails() { return details; }
}
