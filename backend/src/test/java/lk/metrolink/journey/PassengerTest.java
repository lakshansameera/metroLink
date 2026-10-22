package lk.metrolink.journey;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.time.Instant;
import java.time.LocalDate;
import lk.metrolink.journey.domain.Account;
import lk.metrolink.journey.domain.NonResident;
import lk.metrolink.journey.domain.Passenger;
import lk.metrolink.journey.domain.PermanentAccount;
import lk.metrolink.journey.domain.Resident;
import lk.metrolink.journey.domain.TemporaryAccount;
import org.junit.Test;
public class PassengerTest {
    private static final LocalDate VISA_EXPIRY = LocalDate.parse("2026-10-25");

    @Test
    public void aResidentGetsAPermanentAccount() {  
        Passenger nimal = new Passenger(7, "Nimal Perera", "nimal@example.lk", new Resident());

        Account account = nimal.openAccount(100);

        assertTrue(account instanceof PermanentAccount);
        assertEquals("PERMANENT", account.type());
        assertEquals(7, account.getPassengerId());
        assertEquals(100, account.getAccountId());
        assertEquals("a new account starts empty", 0, account.getBalance());
        assertTrue(account.isUsableAt(Instant.parse("2100-01-01T00:00:00Z")));
    }

    @Test
    public void aNonResidentGetsATemporaryAccountThatFollowsTheVisa() { 
        Passenger tourist = new Passenger(8, "Anna Meyer", "anna@example.com", new NonResident(VISA_EXPIRY));

        Account account = tourist.openAccount(101);

        assertTrue(account instanceof TemporaryAccount);
        assertEquals("TEMPORARY", account.type());
        assertEquals(VISA_EXPIRY, ((TemporaryAccount) account).getVisaExpiry());
        assertEquals(0, account.getBalance());
        assertTrue(account.isUsableAt(Instant.parse("2026-10-25T18:29:59Z")));
        assertFalse(account.isUsableAt(Instant.parse("2026-10-25T18:30:00Z")));
    }

    @Test
    public void residencyNotNationalityDecidesTheAccount() {
        // Same person details, different residency: different account. Nationality is not stored.
        Account resident = new Passenger(9, "Kenji Sato", "kenji@example.jp", new Resident()).openAccount(1);
        Account visitor = new Passenger(9, "Kenji Sato", "kenji@example.jp", new NonResident(VISA_EXPIRY)).openAccount(1);

        assertEquals("PERMANENT", resident.type());
        assertEquals("TEMPORARY", visitor.type());
    }

    @Test
    public void residenciesKnowTheirNames() {
        assertEquals("RESIDENT", new Resident().name());
        assertEquals("NON_RESIDENT", new NonResident(VISA_EXPIRY).name());
    }

    @Test
    public void aNonResidentMustHaveAVisaExpiryDate() {
        assertThrows(NullPointerException.class, () -> new NonResident(null));
    }
}
