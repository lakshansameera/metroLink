package lk.metrolink.journey.domain;

/** A business-rule failure. The web layer maps code and status to the HTTP response. */
public class DomainException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    private final String code;
    private final int status;

    public DomainException(String message, String code, int status) {
        super(message);
        this.code = code;
        this.status = status;
    }

    public String code() { return code; }

    public int status() { return status; }
}
