package lk.metrolink.journey.repository;

import lk.metrolink.journey.domain.DomainException;


public class ConstraintViolationException extends DomainException {
    private static final long serialVersionUID = 1L;

    public ConstraintViolationException(String message) {
        super(message, "CONSTRAINT_VIOLATION", 409);
    }
}