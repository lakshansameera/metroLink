package lk.metrolink.journey;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.time.Instant;
import java.time.LocalDate;
import lk.metrolink.journey.domain.Account;
import lk.metrolink.journey.domain.DomainException;
import lk.metrolink.journey.domain.InsufficientCreditException;
import lk.metrolink.journey.domain.TemporaryAccount;
import org.junit.Test;

public class TemporaryAccountTest {
    private static final LocalDate VISA_EXPIRY = LocalDate.parse("2026-10-25");
    private static final Instant LAST_SECOND_OF_VISA_DAY = Instant.parse("2026-10-25T18:29:59Z");
    private static final Instant FIRST_SECOND_AFTER_VISA = Instant.parse("2026-10-25T18:30:00Z");

    private static Account account(long balance) {
        return new TemporaryAccount(balance, 1, 1, VISA_EXPIRY);
    }

    @Test
    public void isUsableThroughTheWholeVisaExpiryDay() {
        Account a = account(100);

        assertEquals("TEMPORARY", a.type());
        assertTrue(a.isUsableAt(Instant.parse("2026-10-01T04:00:00Z")));
        assertTrue(a.isUsableAt(LAST_SECOND_OF_VISA_DAY));
        assertFalse(a.isUsableAt(FIRST_SECOND_AFTER_VISA));
    }

    @Test
    public void anExpiredAccountCannotBeDebitedOrToppedUp() {
        Account a = account(100);

        DomainException debit = assertThrows(DomainException.class, () -> a.deduct(10, FIRST_SECOND_AFTER_VISA));
        assertEquals("ACCOUNT_EXPIRED", debit.code());
        assertEquals(422, debit.status());

        DomainException topUp = assertThrows(DomainException.class, () -> a.credit(10, FIRST_SECOND_AFTER_VISA));
        assertEquals("ACCOUNT_EXPIRED", topUp.code());

        assertEquals("refused operations change nothing", 100, a.getBalance());
    }

    @Test
    public void anExpiredAccountCannotPayEvenWithEnoughCredit() {
        Account a = account(100);

        assertTrue(a.canPay(100, LAST_SECOND_OF_VISA_DAY));
        assertFalse(a.canPay(100, FIRST_SECOND_AFTER_VISA));
    }

    @Test
    public void theBalanceRuleIsTheSameAsForAPermanentAccount() {
        Account a = account(50);

        InsufficientCreditException e = assertThrows(InsufficientCreditException.class,
                () -> a.deduct(51, LAST_SECOND_OF_VISA_DAY));

        assertEquals(50, e.available());
        assertEquals(50, a.getBalance());
        assertEquals(0, a.deduct(50, LAST_SECOND_OF_VISA_DAY));
    }

    @Test
    public void itKnowsItsVisaExpiryDate() {
        assertEquals(VISA_EXPIRY, new TemporaryAccount(0, 1, 1, VISA_EXPIRY).getVisaExpiry());
    }
}
