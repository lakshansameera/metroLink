package lk.metrolink.journey;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.time.Instant;

import lk.metrolink.journey.domain.Account;
import lk.metrolink.journey.domain.DomainException;
import lk.metrolink.journey.domain.InsufficientCreditException;
import lk.metrolink.journey.domain.PermanentAccount;
import org.junit.Test;

public class AccountTest {

    private static final Instant NOW = Instant.parse("2026-10-21T04:00:00Z");

    @Test
    public void aBalanceCanNeverGoNegative() {
        Account a = new PermanentAccount(50, 1, 1);

        InsufficientCreditException e =
                assertThrows(InsufficientCreditException.class, () -> a.deduct(51, NOW));

        assertEquals(51, e.required());
        assertEquals(50, e.available());
        assertEquals("a refused deduction changes nothing", 50, a.getBalance());
    }

    @Test
    public void aDeductionReturnsTheNewBalanceAndMayEmptyTheAccount() {
        Account a = new PermanentAccount(100, 1, 1);

        assertEquals(70, a.deduct(30, NOW));
        assertEquals(70, a.getBalance());
        assertEquals(0, a.deduct(70, NOW));
    }

    @Test
    public void canPayNeedsEnoughCredit() {
        Account a = new PermanentAccount(50, 1, 1);

        assertTrue(a.canPay(50, NOW));
        assertFalse(a.canPay(51, NOW));
    }

    @Test
    public void amountsMustBeSensible() {
        Account a = new PermanentAccount(50, 1, 1);

        DomainException negative = assertThrows(DomainException.class, () -> a.deduct(-1, NOW));
        assertEquals("BAD_AMOUNT", negative.code());
        assertEquals(400, negative.status());

        assertEquals(75, a.credit(25, NOW));
        assertThrows(DomainException.class, () -> a.credit(0, NOW));
        assertEquals("rejected amounts change nothing", 75, a.getBalance());
    }

    @Test
    public void aPermanentAccountNeverExpires() {                    // A3
        Account a = new PermanentAccount(50, 1, 1);
        Instant farFuture = Instant.parse("2100-01-01T00:00:00Z");

        assertEquals("PERMANENT", a.type());
        assertTrue(a.isUsableAt(farFuture));
        assertEquals(40, a.deduct(10, farFuture));
    }
}