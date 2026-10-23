package lk.metrolink.journey.repository;

/**
 * The database failed in a way the program cannot fix, such as a lost connection. A broken rule (a duplicate email,
 * say) is a {@link ConstraintViolationException} instead.
 */
public class DataAccessException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public DataAccessException(String message, Throwable cause) {
        super(message, cause);
    }
}
