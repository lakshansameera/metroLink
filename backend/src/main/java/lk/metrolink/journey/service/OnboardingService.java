package lk.metrolink.journey.service;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import lk.metrolink.journey.domain.Dates;
import lk.metrolink.journey.domain.DomainException;
import lk.metrolink.journey.domain.InvalidRegistrationException;
import lk.metrolink.journey.domain.OnboardingRequest;
import lk.metrolink.journey.domain.Passenger;
import lk.metrolink.journey.domain.PasswordHash;
import lk.metrolink.journey.repository.AccountRepository;
import lk.metrolink.journey.repository.CredentialRepository;
import lk.metrolink.journey.repository.PassengerRepository;
import lk.metrolink.journey.repository.UnitOfWork;
import lk.metrolink.journey.service.view.PassengerView;

/**
 * Customer onboarding: the "AuthSystem" of the Top Up Credit sequence diagram.
 *
 * <pre>
 *   register(name, email, password)            ->  register()
 *     createUser(name, email, password)        ->  passengers.insert, credentials.insert
 *     createAccount(userId, initialCredit=0)   ->  accounts.insert(passenger.openAccount(0))
 *   registrationSuccess(userId)                ->  AuthResult
 *
 *   login(credentials) / validateCredentials   ->  login()
 *   loginSuccess(userId) or loginFailed()      ->  AuthResult or INVALID_LOGIN (401)
 * </pre>
 *
 * <p>This class only coordinates. The request knows what is acceptable, the residency knows which account to open,
 * and the password hash knows how to check a password. Registration is one unit of work, so the customer, the password
 * and the account exist together or not at all (rule O5).
 */
public final class OnboardingService {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);

    /**
     * A hash nobody can match. A login for an email we do not know is checked against it, so that "unknown email" and
     * "wrong password" take the same time as well as giving the same answer (rule O8).
     */
    private static final PasswordHash NOBODY = PasswordHash.of("this password belongs to nobody");

    private final UnitOfWork uow;
    private final PassengerRepository passengers;
    private final AccountRepository accounts;
    private final CredentialRepository credentials;
    private final QueryService queries;
    private final int hashIterations;

    public OnboardingService(UnitOfWork uow, PassengerRepository passengers, AccountRepository accounts,
                             CredentialRepository credentials, QueryService queries) {
        this(uow, passengers, accounts, credentials, queries, PasswordHash.DEFAULT_ITERATIONS);
    }

    /** Same, with a chosen number of hash repeats. Tests pass a small number so they stay fast. */
    public OnboardingService(UnitOfWork uow, PassengerRepository passengers, AccountRepository accounts,
                             CredentialRepository credentials, QueryService queries, int hashIterations) {
        this.uow = uow;
        this.passengers = passengers;
        this.accounts = accounts;
        this.credentials = credentials;
        this.queries = queries;
        this.hashIterations = hashIterations;
    }

    public AuthResult register(OnboardingRequest request) {
        return register(request, Dates.now());
    }

    /** {@code at} is the moment of registration; its Colombo date decides whether a visa date is still in the future. */
    public AuthResult register(OnboardingRequest request, Instant at) {
        List<OnboardingRequest.Problem> problems = request.problems(Dates.localDate(at));       // O1, O3
        if (!problems.isEmpty()) {
            throw new InvalidRegistrationException(problems);
        }
        // Hashing is slow on purpose, so it is done before the lock is taken.
        PasswordHash hash = PasswordHash.of(request.getPassword(), hashIterations);             // O6

        return uow.run(() -> {
            if (passengers.findByEmail(request.getEmail()).isPresent()) {                       // O2
                throw new DomainException("This email is already registered", "EMAIL_TAKEN", 409);
            }
            Passenger passenger = passengers.insert(
                    new Passenger(0, request.getName(), request.getEmail(), request.residency()));   // createUser
            credentials.insert(passenger.getPassengerId(), hash);
            accounts.insert(passenger.openAccount(0));                                          // createAccount, O3, O4

            PassengerView customer = queries.passenger(passenger.getPassengerId());              // O9
            return new AuthResult(customer.getPassengerId(), welcome(customer), customer);      // registrationSuccess
        });
    }

    /** Checks an email and password. The answer is the same for a wrong password and an unknown email (rule O8). */
    public AuthResult login(String email, String password) {
        String wanted = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
        String typed = password == null ? "" : password;

        Optional<Passenger> found = uow.run(() -> passengers.findByEmail(wanted));
        Optional<PasswordHash> stored = found.flatMap(p -> uow.run(() -> credentials.findByPassenger(p.getPassengerId())));

        // Always run one hash check, even when there is nobody to check against.
        boolean passwordMatches = stored.orElse(NOBODY).matches(typed);
        if (stored.isEmpty() || !passwordMatches) {
            throw new DomainException("Invalid login", "INVALID_LOGIN", 401);
        }
        PassengerView customer = queries.passenger(found.get().getPassengerId());               // loginSuccess
        return new AuthResult(customer.getPassengerId(), "Welcome back, " + customer.getName() + ".", customer);
    }

    private static String welcome(PassengerView customer) {
        String accountKind = customer.getAccountType().toLowerCase(Locale.ROOT);
        String validity = customer.getVisaExpiry() == null
                ? ""
                : " and works through " + DAY.format(customer.getVisaExpiry());
        return "Welcome, " + customer.getName() + ". Your " + accountKind + " account is open with LKR "
                + customer.getBalance() + " credit" + validity + ".";
    }
}
