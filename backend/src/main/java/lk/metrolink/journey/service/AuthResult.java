package lk.metrolink.journey.service;

import lk.metrolink.journey.service.view.PassengerView;

/**
 * The answer to a successful registration or login: who the customer is, and one sentence to show them.
 * {@code userId} is the id the sequence diagram passes back in {@code registrationSuccess(userId)} and
 * {@code loginSuccess(userId)}. It never carries a password or a hash.
 */
public final class AuthResult {
    private final long userId;
    private final String message;
    private final PassengerView customer;

    public AuthResult(long userId, String message, PassengerView customer) {
        this.userId = userId;
        this.message = message;
        this.customer = customer;
    }

    public long getUserId() { return userId; }
    public String getMessage() { return message; }
    public PassengerView getCustomer() { return customer; }
}
