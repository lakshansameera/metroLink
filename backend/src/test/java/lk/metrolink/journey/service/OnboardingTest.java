package lk.metrolink.journey.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lk.metrolink.journey.domain.Account;
import lk.metrolink.journey.domain.DomainException;
import lk.metrolink.journey.domain.InvalidRegistrationException;
import lk.metrolink.journey.domain.OnboardingRequest;
import lk.metrolink.journey.domain.PasswordHash;
import lk.metrolink.journey.domain.ResidencyKind;
import lk.metrolink.journey.repository.AccountRepository;
import lk.metrolink.journey.repository.CredentialRepository;
import lk.metrolink.journey.repository.memory.InMemoryAccountRepository;
import lk.metrolink.journey.repository.memory.InMemoryCredentialRepository;
import lk.metrolink.journey.repository.memory.InMemoryDatabase;
import lk.metrolink.journey.repository.memory.InMemoryFareTransactionRepository;
import lk.metrolink.journey.repository.memory.InMemoryJourneyRepository;
import lk.metrolink.journey.repository.memory.InMemoryPassengerRepository;
import lk.metrolink.journey.repository.memory.InMemoryRouteRepository;
import lk.metrolink.journey.repository.memory.InMemoryTicketRepository;
import lk.metrolink.journey.service.view.PassengerView;
import org.junit.Before;
import org.junit.Test;

/**
 * Customer onboarding, from the "New user - Registration" and "Existing user - Login" branches of the Top Up Credit
 * sequence. Each test names the rule it proves (O1 to O9 in docs/rules.md).
 */
public class OnboardingTest {

    private static final LocalDate TODAY = LocalDate.parse("2026-10-21");
    /** 09:30 in Colombo on TODAY. */
    private static final Instant MORNING = Instant.parse("2026-10-21T04:00:00Z");
    private static final int FAST = 1_000;
    private static final String DEMO_PASSWORD = "Demo@1234";

    private InMemoryDatabase db;
    private InMemoryPassengerRepository passengers;
    private InMemoryAccountRepository accounts;
    private InMemoryCredentialRepository credentials;
    private QueryService queries;
    private OnboardingService onboarding;

    @Before
    public void setUp() {
        db = InMemoryDatabase.seeded(TODAY);
        passengers = new InMemoryPassengerRepository(db);
        accounts = new InMemoryAccountRepository(db);
        credentials = new InMemoryCredentialRepository(db);
        queries = new QueryService(db, passengers, accounts, new InMemoryTicketRepository(db),
                new InMemoryJourneyRepository(db), new InMemoryFareTransactionRepository(db),
                new InMemoryRouteRepository(db));
        onboarding = new OnboardingService(db, passengers, accounts, credentials, queries, FAST);
    }

    private static OnboardingRequest resident(String name, String email) {
        return new OnboardingRequest(name, email, "Sunny day 42", ResidencyKind.RESIDENT, null);
    }

    private static OnboardingRequest nonResident(String name, String email, LocalDate visaExpiry) {
        return new OnboardingRequest(name, email, "Sunny day 42", ResidencyKind.NON_RESIDENT, visaExpiry);
    }

    private AuthResult register(OnboardingRequest request) {
        return onboarding.register(request, MORNING);
    }

    private int passengerCount() {
        return queries.passengers().size();
    }

    private void expectCode(String code, int status, Runnable work) {
        try {
            work.run();
            fail("expected " + code);
        } catch (DomainException e) {
            assertEquals(code, e.code());
            assertEquals(status, e.status());
        }
    }

    // ---- registration: the happy paths (O3, O4, O9)

    @Test
    public void aResidentGetsAPermanentAccountWithNoCredit() {
        AuthResult result = register(resident("Amaya Jayasuriya", "amaya@example.com"));

        PassengerView customer = result.getCustomer();
        assertEquals("Amaya Jayasuriya", customer.getName());
        assertEquals("amaya@example.com", customer.getEmail());
        assertEquals("PERMANENT", customer.getAccountType());
        assertEquals(0, customer.getBalance());
        assertNull(customer.getVisaExpiry());
    }

    @Test
    public void aNonResidentGetsATemporaryAccountThatKnowsTheVisaDate() {
        AuthResult result = register(nonResident("Emma Walker", "emma.w@example.com", LocalDate.parse("2026-12-31")));

        PassengerView customer = result.getCustomer();
        assertEquals("TEMPORARY", customer.getAccountType());
        assertEquals(0, customer.getBalance());
        assertEquals(LocalDate.parse("2026-12-31"), customer.getVisaExpiry());
    }

    @Test
    public void theUserIdIsTheNewCustomersId() {
        AuthResult result = register(resident("Amaya", "amaya@example.com"));

        assertEquals(result.getCustomer().getPassengerId(), result.getUserId());
    }

    @Test
    public void theWelcomeSaysWhatWasOpened() {
        assertEquals("Welcome, Amaya. Your permanent account is open with LKR 0 credit.",
                register(resident("Amaya", "amaya@example.com")).getMessage());
        assertEquals("Welcome, Emma. Your temporary account is open with LKR 0 credit and works through 31 Dec 2026.",
                register(nonResident("Emma", "emma@walker.example", LocalDate.parse("2026-12-31"))).getMessage());
    }

    @Test
    public void newIdsStartAfterTheDemoCustomers() {
        PassengerView first = register(resident("Amaya", "amaya@example.com")).getCustomer();
        PassengerView second = register(resident("Binura", "binura@example.com")).getCustomer();

        assertEquals(6, first.getPassengerId());
        assertEquals(6, first.getAccountId());
        assertEquals(7, second.getPassengerId());
        assertEquals(7, second.getAccountId());
    }

    @Test
    public void theNewCustomerAppearsInThePassengerListAtOnce() {
        int before = passengerCount();

        register(resident("Amaya", "amaya@example.com"));

        List<PassengerView> list = queries.passengers();
        assertEquals(before + 1, list.size());
        PassengerView last = list.get(list.size() - 1);
        assertEquals("Amaya", last.getName());
        assertEquals("PERMANENT", last.getAccountType());
        assertEquals(0, last.getBalance());
    }

    @Test
    public void theNewCustomerCanBeCreditedLikeAnyOtherAccount() {
        long id = register(resident("Amaya", "amaya@example.com")).getUserId();

        db.execute(() -> {
            Account account = accounts.findByPassenger(id).orElseThrow();
            account.credit(250, MORNING);
            accounts.save(account);
        });

        assertEquals(250, queries.passenger(id).getBalance());
    }

    // ---- registration: what is refused (O1, O2, O3)

    @Test
    public void everyWrongFieldIsReportedAndNothingIsCreated() {
        int before = passengerCount();

        try {
            register(new OnboardingRequest("", "not-an-email", "short", null, null));
            fail("expected InvalidRegistrationException");
        } catch (InvalidRegistrationException e) {
            List<String> fields = new ArrayList<>();
            e.problems().forEach(p -> fields.add(p.getField()));
            assertEquals(List.of("name", "email", "password", "residency"), fields);
            assertEquals("VALIDATION", e.code());
            assertEquals(400, e.status());
        }
        assertEquals(before, passengerCount());
    }

    @Test
    public void aVisaThatEndsTodayIsAccepted() {
        AuthResult result = register(nonResident("Emma", "emma@walker.example", TODAY));

        assertEquals("TEMPORARY", result.getCustomer().getAccountType());
    }

    @Test
    public void aVisaThatEndedYesterdayIsRefused() {
        int before = passengerCount();

        try {
            register(nonResident("Emma", "emma@walker.example", TODAY.minusDays(1)));
            fail("expected InvalidRegistrationException");
        } catch (InvalidRegistrationException e) {
            assertEquals("visaExpiry", e.problems().get(0).getField());
        }
        assertEquals(before, passengerCount());
    }

    @Test
    public void todayForTheVisaCheckIsTheColomboDateNotTheUtcDate() {
        // 19:00 UTC on 20 Oct is 00:30 on 21 Oct in Colombo, so a visa ending 21 Oct is still good.
        Instant justAfterColomboMidnight = Instant.parse("2026-10-20T19:00:00Z");
        AuthResult ok = onboarding.register(nonResident("Emma", "emma@walker.example", TODAY), justAfterColomboMidnight);
        assertEquals("TEMPORARY", ok.getCustomer().getAccountType());

        // 19:00 UTC on 21 Oct is 00:30 on 22 Oct in Colombo, so the same visa has already ended.
        Instant nextColomboDay = Instant.parse("2026-10-21T19:00:00Z");
        try {
            onboarding.register(nonResident("Fiona", "fiona@walker.example", TODAY), nextColomboDay);
            fail("expected InvalidRegistrationException");
        } catch (InvalidRegistrationException expected) {
            assertEquals("visaExpiry", expected.problems().get(0).getField());
        }
    }

    @Test
    public void anEmailThatIsAlreadyRegisteredIsRefused() {
        register(resident("Amaya", "amaya@example.com"));
        int before = passengerCount();

        expectCode("EMAIL_TAKEN", 409, () -> register(resident("Someone Else", "amaya@example.com")));

        assertEquals(before, passengerCount());
    }

    @Test
    public void theEmailCheckIgnoresCaseAndSurroundingSpaces() {
        register(resident("Amaya", "amaya@example.com"));

        expectCode("EMAIL_TAKEN", 409, () -> register(resident("Copy", "  AMAYA@Example.COM ")));
    }

    @Test
    public void aDemoCustomersEmailIsAlsoTaken() {
        expectCode("EMAIL_TAKEN", 409, () -> register(resident("Another Nimal", "nimal@example.com")));
    }

    @Test
    public void aRefusedRegistrationDoesNotUseUpAnId() {
        expectCode("EMAIL_TAKEN", 409, () -> register(resident("Copy", "nimal@example.com")));

        assertEquals(6, register(resident("Amaya", "amaya@example.com")).getUserId());
    }

    // ---- registration: all or nothing (O5)

    private OnboardingService withAccountRepository(AccountRepository accountRepository) {
        return new OnboardingService(db, passengers, accountRepository, credentials, queries, FAST);
    }

    private OnboardingService withCredentialRepository(CredentialRepository credentialRepository) {
        return new OnboardingService(db, passengers, accounts, credentialRepository, queries, FAST);
    }

    @Test
    public void ifTheAccountCannotBeOpenedTheCustomerAndPasswordAreNotKept() {
        AccountRepository failing = new AccountRepository() {
            @Override public Optional<Account> findById(long accountId) { return accounts.findById(accountId); }
            @Override public Optional<Account> findByPassenger(long id) { return accounts.findByPassenger(id); }
            @Override public Account insert(Account draft) { throw new IllegalStateException("disk full"); }
            @Override public void save(Account account) { accounts.save(account); }
        };
        int before = passengerCount();

        try {
            withAccountRepository(failing).register(resident("Amaya", "amaya@example.com"), MORNING);
            fail("expected IllegalStateException");
        } catch (IllegalStateException expected) {
            assertEquals("disk full", expected.getMessage());
        }

        assertEquals(before, passengerCount());
        assertTrue(db.run(() -> passengers.findByEmail("amaya@example.com")).isEmpty());
        assertTrue(db.run(() -> credentials.findByPassenger(6)).isEmpty());
        assertEquals(6, register(resident("Amaya", "amaya@example.com")).getUserId());     // id 6 was not lost
    }

    @Test
    public void ifThePasswordCannotBeKeptTheCustomerIsNotKeptEither() {
        CredentialRepository failing = new CredentialRepository() {
            @Override public void insert(long passengerId, PasswordHash hash) { throw new IllegalStateException("disk full"); }
            @Override public Optional<PasswordHash> findByPassenger(long id) { return credentials.findByPassenger(id); }
        };
        int before = passengerCount();

        try {
            withCredentialRepository(failing).register(resident("Amaya", "amaya@example.com"), MORNING);
            fail("expected IllegalStateException");
        } catch (IllegalStateException expected) {
            assertEquals("disk full", expected.getMessage());
        }

        assertEquals(before, passengerCount());
        assertTrue(db.run(() -> accounts.findByPassenger(6)).isEmpty());
    }

    // ---- the password (O6)

    @Test
    public void thePasswordIsKeptOnlyAsASaltedHash() {
        long id = register(resident("Amaya", "amaya@example.com")).getUserId();

        PasswordHash stored = db.run(() -> credentials.findByPassenger(id)).orElseThrow();

        assertTrue(stored.matches("Sunny day 42"));
        assertFalse(stored.encoded().contains("Sunny day 42"));
    }

    @Test
    public void twoCustomersWithTheSamePasswordHaveDifferentStoredHashes() {
        long first = register(resident("Amaya", "amaya@example.com")).getUserId();
        long second = register(resident("Binura", "binura@example.com")).getUserId();

        String a = db.run(() -> credentials.findByPassenger(first)).orElseThrow().encoded();
        String b = db.run(() -> credentials.findByPassenger(second)).orElseThrow().encoded();

        assertFalse(a.equals(b));
    }

    // ---- login (O7, O8)

    @Test
    public void aRegisteredCustomerCanLogInWithTheirPassword() {
        long id = register(resident("Amaya", "amaya@example.com")).getUserId();

        AuthResult result = onboarding.login("amaya@example.com", "Sunny day 42");

        assertEquals(id, result.getUserId());
        assertEquals("Welcome back, Amaya.", result.getMessage());
        assertEquals("PERMANENT", result.getCustomer().getAccountType());
    }

    @Test
    public void theEmailAtLoginIgnoresCaseAndSurroundingSpaces() {
        long id = register(resident("Amaya", "amaya@example.com")).getUserId();

        assertEquals(id, onboarding.login("  Amaya@EXAMPLE.com ", "Sunny day 42").getUserId());
    }

    @Test
    public void thePasswordAtLoginIsCaseSensitive() {
        register(resident("Amaya", "amaya@example.com"));

        expectCode("INVALID_LOGIN", 401, () -> onboarding.login("amaya@example.com", "sunny day 42"));
    }

    @Test
    public void aWrongPasswordIsRefused() {
        register(resident("Amaya", "amaya@example.com"));

        expectCode("INVALID_LOGIN", 401, () -> onboarding.login("amaya@example.com", "Rainy day 42"));
    }

    @Test
    public void anUnknownEmailGetsTheSameAnswerAsAWrongPassword() {
        register(resident("Amaya", "amaya@example.com"));
        DomainException wrongPassword = null;
        DomainException unknownEmail = null;

        try {
            onboarding.login("amaya@example.com", "Rainy day 42");
        } catch (DomainException e) {
            wrongPassword = e;
        }
        try {
            onboarding.login("nobody@example.com", "Sunny day 42");
        } catch (DomainException e) {
            unknownEmail = e;
        }

        assertEquals(wrongPassword.code(), unknownEmail.code());
        assertEquals(wrongPassword.status(), unknownEmail.status());
        assertEquals(wrongPassword.getMessage(), unknownEmail.getMessage());
    }

    @Test
    public void missingEmailOrPasswordIsAnInvalidLoginNotACrash() {
        expectCode("INVALID_LOGIN", 401, () -> onboarding.login(null, "Sunny day 42"));
        expectCode("INVALID_LOGIN", 401, () -> onboarding.login("amaya@example.com", null));
        expectCode("INVALID_LOGIN", 401, () -> onboarding.login(null, null));
    }

    @Test
    public void theFiveDemoCustomersCanLogInWithTheDemoPassword() {
        String[] emails = {"nimal@example.com", "sanduni@example.com", "kasun@example.com", "emma@example.com",
                "hiro@example.com"};
        for (int i = 0; i < emails.length; i++) {
            AuthResult result = onboarding.login(emails[i], DEMO_PASSWORD);
            assertEquals(i + 1, result.getUserId());
        }
    }

    @Test
    public void aCustomerWhoseVisaHasEndedCanStillLogIn() {
        // Logging in is not riding: the account is only refused at the reader, where the visa date is checked.
        AuthResult result = onboarding.login("hiro@example.com", DEMO_PASSWORD);

        assertEquals("TEMPORARY", result.getCustomer().getAccountType());
    }

    @Test
    public void loggingInChangesNothing() {
        register(resident("Amaya", "amaya@example.com"));
        int before = passengerCount();
        long balance = queries.passenger(6).getBalance();

        onboarding.login("amaya@example.com", "Sunny day 42");
        try {
            onboarding.login("amaya@example.com", "wrong");
        } catch (DomainException expected) {
            // fine
        }

        assertEquals(before, passengerCount());
        assertEquals(balance, queries.passenger(6).getBalance());
    }
}
