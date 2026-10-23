package lk.metrolink.journey;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import lk.metrolink.journey.domain.NonResident;
import lk.metrolink.journey.domain.OnboardingRequest;
import lk.metrolink.journey.domain.OnboardingRequest.Problem;
import lk.metrolink.journey.domain.Resident;
import lk.metrolink.journey.domain.ResidencyKind;
import org.junit.Test;

/** O1, O2 and O3: what a registration may contain, judged without touching any storage. */
public class OnboardingRequestTest {

    private static final LocalDate TODAY = LocalDate.parse("2026-10-21");

    private static OnboardingRequest resident(String name, String email, String password) {
        return new OnboardingRequest(name, email, password, ResidencyKind.RESIDENT, null);
    }

    private static OnboardingRequest nonResident(LocalDate visaExpiry) {
        return new OnboardingRequest("Emma Walker", "emma.w@example.com", "Sunny day 42",
                ResidencyKind.NON_RESIDENT, visaExpiry);
    }

    private static List<String> fieldsWithProblems(OnboardingRequest request) {
        List<String> fields = new ArrayList<>();
        for (Problem problem : request.problems(TODAY)) {
            fields.add(problem.getField());
        }
        return fields;
    }

    private static String messageFor(OnboardingRequest request, String field) {
        for (Problem problem : request.problems(TODAY)) {
            if (problem.getField().equals(field)) {
                return problem.getMessage();
            }
        }
        return null;
    }

    @Test
    public void aCompleteResidentRegistrationHasNoProblems() {
        assertTrue(resident("Amaya Jayasuriya", "amaya@example.com", "Sunny day 42").problems(TODAY).isEmpty());
    }

    @Test
    public void aCompleteNonResidentRegistrationHasNoProblems() {
        assertTrue(nonResident(LocalDate.parse("2026-12-31")).problems(TODAY).isEmpty());
    }

    @Test
    public void nameAndEmailAreTrimmedAndTheEmailIsLowerCased() {
        OnboardingRequest request = resident("  Amaya Jayasuriya ", "  Amaya@Example.COM ", "Sunny day 42");

        assertEquals("Amaya Jayasuriya", request.getName());
        assertEquals("amaya@example.com", request.getEmail());
    }

    @Test
    public void thePasswordIsKeptExactlyAsTyped() {
        assertEquals("  spaced out 1 ", resident("A", "a@b.co", "  spaced out 1 ").getPassword());
    }

    @Test
    public void everythingMissingIsReportedAtOnce() {
        OnboardingRequest request = new OnboardingRequest(null, null, null, null, null);

        assertEquals(List.of("name", "email", "password", "residency"), fieldsWithProblems(request));
    }

    @Test
    public void aBlankNameIsRefused() {
        assertEquals(List.of("name"), fieldsWithProblems(resident("   ", "a@b.co", "Sunny day 42")));
    }

    @Test
    public void aNameOverOneHundredCharactersIsRefused() {
        String longName = "A".repeat(OnboardingRequest.NAME_MAX + 1);

        assertEquals(List.of("name"), fieldsWithProblems(resident(longName, "a@b.co", "Sunny day 42")));
        assertTrue(resident("A".repeat(OnboardingRequest.NAME_MAX), "a@b.co", "Sunny day 42").problems(TODAY).isEmpty());
    }

    @Test
    public void anEmailThatDoesNotLookLikeAnAddressIsRefused() {
        for (String bad : new String[] {"amaya", "amaya@", "@example.com", "amaya@example", "am aya@example.com",
                "a@@b.co"}) {
            assertEquals(bad, List.of("email"), fieldsWithProblems(resident("Amaya", bad, "Sunny day 42")));
        }
    }

    @Test
    public void aPasswordUnderEightCharactersIsRefusedAndEightIsEnough() {
        assertEquals(List.of("password"), fieldsWithProblems(resident("Amaya", "a@b.co", "seven77")));
        assertTrue(resident("Amaya", "a@b.co", "eight888").problems(TODAY).isEmpty());
    }

    @Test
    public void aPasswordOverOneHundredTwentyEightCharactersIsRefused() {
        assertEquals(List.of("password"),
                fieldsWithProblems(resident("Amaya", "a@b.co", "x".repeat(OnboardingRequest.PASSWORD_MAX + 1))));
        assertTrue(resident("Amaya", "a@b.co", "x".repeat(OnboardingRequest.PASSWORD_MAX)).problems(TODAY).isEmpty());
    }

    @Test
    public void aNonResidentMustGiveAVisaExpiryDate() {
        assertEquals(List.of("visaExpiry"), fieldsWithProblems(nonResident(null)));
        assertEquals("Visa expiry date is required for a non-resident",
                messageFor(nonResident(null), "visaExpiry"));
    }

    @Test
    public void aVisaThatEndsTodayIsStillAccepted() {
        assertTrue(nonResident(TODAY).problems(TODAY).isEmpty());
    }

    @Test
    public void aVisaThatEndedYesterdayIsRefused() {
        assertEquals(List.of("visaExpiry"), fieldsWithProblems(nonResident(TODAY.minusDays(1))));
        assertEquals("Visa expiry date must be today or later",
                messageFor(nonResident(TODAY.minusDays(1)), "visaExpiry"));
    }

    @Test
    public void aResidentMustNotGiveAVisaDate() {
        OnboardingRequest request = new OnboardingRequest("Amaya", "a@b.co", "Sunny day 42",
                ResidencyKind.RESIDENT, TODAY.plusDays(30));

        assertEquals(List.of("visaExpiry"), fieldsWithProblems(request));
    }

    @Test
    public void aResidentRequestMakesAResidencyThatOpensAPermanentAccount() {
        assertTrue(resident("Amaya", "a@b.co", "Sunny day 42").residency() instanceof Resident);
    }

    @Test
    public void aNonResidentRequestMakesAResidencyCarryingTheVisaDate() {
        LocalDate visa = LocalDate.parse("2026-12-31");

        NonResident residency = (NonResident) nonResident(visa).residency();

        assertEquals(visa, residency.getVisaExpiry());
    }
}
