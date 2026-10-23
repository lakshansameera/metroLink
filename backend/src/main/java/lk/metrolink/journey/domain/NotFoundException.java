package lk.metrolink.journey.domain;

/** Something the caller asked for does not exist: an unknown journey, stop, passenger and so on. */
public class NotFoundException extends DomainException {
    private static final long serialVersionUID = 1L;

    public NotFoundException(String what) {
        super(what + " was not found", "NOT_FOUND", 404);
    }
}
