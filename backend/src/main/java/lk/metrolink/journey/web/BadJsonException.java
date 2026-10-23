package lk.metrolink.journey.web;

/** The request body is not valid JSON: reported as HTTP 400 BAD_JSON. */
public class BadJsonException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public BadJsonException() {
        super("Malformed JSON body");
    }
}
