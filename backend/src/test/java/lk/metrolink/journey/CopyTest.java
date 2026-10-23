package lk.metrolink.journey;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.time.Instant;
import java.time.LocalDate;
import lk.metrolink.journey.domain.Account;
import lk.metrolink.journey.domain.Journey;
import lk.metrolink.journey.domain.PermanentAccount;
import lk.metrolink.journey.domain.QRTicket;
import lk.metrolink.journey.domain.TemporaryAccount;
import lk.metrolink.journey.domain.TicketState;
import org.junit.Test;

public class CopyTest {

    private static final Instant NOW = Instant.parse("2026-10-21T04:00:00Z");
    private static final Instant LATER = Instant.parse("2026-10-21T04:40:00Z");
    private static final LocalDate VISA_EXPIRY = LocalDate.parse("2026-10-25");

    private static QRTicket ticket() {
        return new QRTicket(5, "MLK-COPY", 1, "R100", "S1", "S5", LocalDate.parse("2026-10-21"), 69,
                Instant.parse("2026-10-20T04:00:00Z"), TicketState.of("ACTIVE"));
    }

    @Test
    public void aCopiedPermanentAccountHasTheSameDetails() {
        Account original = new PermanentAccount(50, 7, 3);

        Account copy = original.copy();

        assertNotSame(original, copy);
        assertEquals("PERMANENT", copy.type());
        assertEquals(50, copy.getBalance());
        assertEquals(7, copy.getPassengerId());
        assertEquals(3, copy.getAccountId());
    }

    @Test
    public void changesToAnAccountCopyNeverReachTheOriginal() {
        Account original = new PermanentAccount(50, 7, 3);
        Account copy = original.copy();

        copy.deduct(20, NOW);

        assertEquals(30, copy.getBalance());
        assertEquals(50, original.getBalance());
    }

    @Test
    public void aCopiedTemporaryAccountKeepsItsKindAndItsVisaExpiry() {
        Account copy = new TemporaryAccount(50, 7, 3, VISA_EXPIRY).copy();

        assertEquals("TEMPORARY", copy.type());
        assertEquals(VISA_EXPIRY, ((TemporaryAccount) copy).getVisaExpiry());
        assertTrue(copy.isUsableAt(Instant.parse("2026-10-25T18:29:59Z")));
        assertFalse(copy.isUsableAt(Instant.parse("2026-10-25T18:30:00Z")));
    }

    @Test
    public void aTicketCopyIsIndependentOfTheOriginal() {
        QRTicket original = ticket();
        QRTicket copy = original.copy();

        copy.markUsed();

        assertEquals("USED", copy.status());
        assertEquals("ACTIVE", original.status());
        assertEquals("MLK-COPY", copy.getQrCode());
        assertEquals(69, copy.getQuotedFare());
    }

    @Test
    public void aTicketCopyKeepsEveryDetail() {
        QRTicket copy = ticket().copy();

        assertEquals(5, copy.getTicketId());
        assertEquals("MLK-COPY", copy.getQrCode());
        assertEquals(1, copy.getPassengerId());
        assertEquals("R100", copy.getRouteNo());
        assertEquals("S1", copy.getBoardingStopId());
        assertEquals("S5", copy.getPlannedDestinationStopId());
        assertEquals(LocalDate.parse("2026-10-21"), copy.getTravelDate());
        assertEquals(69, copy.getQuotedFare());
        assertEquals(Instant.parse("2026-10-20T04:00:00Z"), copy.getIssuedAt());
        assertEquals("ACTIVE", copy.status());
    }

    @Test
    public void withIdGivesATicketANewIdAndKeepsEverythingElse() {
        QRTicket original = ticket();

        QRTicket stored = original.withId(42);

        assertEquals(42, stored.getTicketId());
        assertEquals(5, original.getTicketId());
        assertEquals("MLK-COPY", stored.getQrCode());
        assertEquals("S5", stored.getPlannedDestinationStopId());
        assertEquals("ACTIVE", stored.status());
    }

    @Test
    public void aJourneyCopyIsIndependentOfTheOriginal() {
        Journey original = Journey.start(5, "R100", "S1", NOW);
        Journey copy = original.copy();

        copy.recordExit("S5", LATER, 69);

        assertEquals("S5", copy.getExitStopId());
        assertEquals(Long.valueOf(69), copy.getFareDue());
        assertNull(original.getExitStopId());
        assertNull(original.getFareDue());
        assertTrue(original.isOpen());
    }

    @Test
    public void aJourneyCopyKeepsEveryRecordedDetail() {
        Journey original = Journey.start(5, "R100", "S1", NOW);
        original.recordExit("S5", LATER, 69);
        original.complete(69);

        Journey copy = original.copy();

        assertEquals(original.getJourneyId(), copy.getJourneyId());
        assertEquals(5, copy.getTicketId());
        assertEquals("R100", copy.getRouteNo());
        assertEquals("S1", copy.getBoardingStopId());
        assertEquals(NOW, copy.getTapInTime());
        assertEquals("S5", copy.getExitStopId());
        assertEquals(LATER, copy.getTapOutTime());
        assertEquals(Long.valueOf(69), copy.getFareDue());
        assertEquals(Long.valueOf(69), copy.getFareCharged());
        assertEquals("COMPLETED", copy.status());
    }

    @Test
    public void aJourneyCopyKeepsMissingValuesMissing() {
        Journey copy = Journey.start(5, "R100", "S1", NOW).copy();

        assertNull(copy.getExitStopId());
        assertNull(copy.getTapOutTime());
        assertNull(copy.getFareDue());
        assertNull(copy.getFareCharged());
    }

    @Test
    public void aCopyKeepsTheCurrentStatus() {
        Journey original = Journey.start(5, "R100", "S1", NOW);
        original.recordExit("S5", LATER, 69);
        original.markPaymentPending();

        Journey copy = original.copy();
        copy.complete(69);

        assertEquals("COMPLETED", copy.status());
        assertEquals("PAYMENT_PENDING", original.status());
        assertNull(original.getFareCharged());
    }

    @Test
    public void withIdGivesAJourneyANewIdAndKeepsEverythingElse() {
        Journey draft = Journey.start(5, "R100", "S1", NOW);

        Journey stored = draft.withId(9);

        assertEquals(9, stored.getJourneyId());
        assertEquals(0, draft.getJourneyId());
        assertEquals(5, stored.getTicketId());
        assertEquals("S1", stored.getBoardingStopId());
        assertEquals(NOW, stored.getTapInTime());
        assertEquals("IN_PROGRESS", stored.status());
    }
}