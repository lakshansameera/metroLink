package lk.metrolink.journey;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.time.Instant;
import java.util.List;
import lk.metrolink.journey.domain.Alert;
import lk.metrolink.journey.domain.AlertAudience;
import lk.metrolink.journey.domain.AlertStage;
import lk.metrolink.journey.domain.AlertType;
import lk.metrolink.journey.domain.InvalidTicketReason;
import org.junit.Test;

public class AlertTest {

    private static final Instant RAISED = Instant.parse("2026-10-21T04:40:00Z");
    private static final Instant SETTLED = Instant.parse("2026-10-21T09:00:00Z");

    private static Alert paymentPending() {
        return Alert.raise(AlertType.PAYMENT_PENDING, AlertStage.EXIT, "Fare LKR 69 unpaid", RAISED,
                AlertAudience.DRIVER, AlertAudience.INSPECTOR);
    }

    @Test
    public void aNewAlertIsAnOpenDraftWithNoDetailsYet() {
        Alert alert = paymentPending();

        assertEquals(0, alert.getAlertId());
        assertEquals(AlertType.PAYMENT_PENDING, alert.getType());
        assertEquals(AlertStage.EXIT, alert.getStage());
        assertEquals("Fare LKR 69 unpaid", alert.getMessage());
        assertEquals(RAISED, alert.getCreatedAt());
        assertTrue(alert.isOpen());
        assertNull(alert.getResolvedAt());
        assertNull(alert.getJourneyId());
        assertNull(alert.getTicketId());
        assertNull(alert.getQrCode());
        assertNull(alert.getReason());
    }

    @Test
    public void detailsAreAddedByMakingANewAlertAndTheOriginalIsUntouched() {
        Alert plain = paymentPending();

        Alert detailed = plain.forJourney(12).forTicket(5).forQrCode("MLK-X").because(InvalidTicketReason.ALREADY_BOARDED);

        assertEquals(Long.valueOf(12), detailed.getJourneyId());
        assertEquals(Long.valueOf(5), detailed.getTicketId());
        assertEquals("MLK-X", detailed.getQrCode());
        assertEquals(InvalidTicketReason.ALREADY_BOARDED, detailed.getReason());
        assertNull(plain.getJourneyId());
        assertNull(plain.getReason());
    }

    @Test
    public void addingDetailsKeepsTheEarlierOnes() {
        Alert alert = paymentPending().forJourney(12).forTicket(5);

        assertEquals(Long.valueOf(12), alert.getJourneyId());
        assertEquals(Long.valueOf(5), alert.getTicketId());
        assertEquals(AlertType.PAYMENT_PENDING, alert.getType());
    }

    @Test
    public void itKnowsWhoMustSeeIt() {
        Alert both = paymentPending();
        Alert driverOnly = Alert.raise(AlertType.INVALID_TICKET, AlertStage.BOARDING, "Bad ticket", RAISED,
                AlertAudience.DRIVER);

        assertTrue(both.isFor(AlertAudience.DRIVER));
        assertTrue(both.isFor(AlertAudience.INSPECTOR));
        assertTrue(driverOnly.isFor(AlertAudience.DRIVER));
        assertFalse(driverOnly.isFor(AlertAudience.INSPECTOR));
    }

    @Test
    public void theAudienceListCannotBeChanged() {
        Alert alert = paymentPending();

        assertEquals(List.of(AlertAudience.DRIVER, AlertAudience.INSPECTOR), alert.getAudience());
        assertThrows(UnsupportedOperationException.class, () -> alert.getAudience().clear());
    }

    @Test
    public void resolvingGivesAClosedCopyAndLeavesTheOriginalOpen() {       // D1
        Alert open = paymentPending().forJourney(12);

        Alert closed = open.resolve(SETTLED);

        assertFalse(closed.isOpen());
        assertEquals(SETTLED, closed.getResolvedAt());
        assertEquals(Long.valueOf(12), closed.getJourneyId());
        assertTrue(open.isOpen());
    }

    @Test
    public void resolvingTwiceKeepsTheFirstTime() {                         // D1
        Alert closed = paymentPending().resolve(SETTLED);

        Alert again = closed.resolve(Instant.parse("2026-10-22T09:00:00Z"));

        assertSame(closed, again);
        assertEquals(SETTLED, again.getResolvedAt());
    }

    @Test
    public void withIdGivesANewIdAndKeepsEverythingElse() {
        Alert draft = paymentPending().forJourney(12).forTicket(5).forQrCode("MLK-X");

        Alert stored = draft.withId(31);

        assertEquals(31, stored.getAlertId());
        assertEquals(0, draft.getAlertId());
        assertEquals(Long.valueOf(12), stored.getJourneyId());
        assertEquals(Long.valueOf(5), stored.getTicketId());
        assertEquals("MLK-X", stored.getQrCode());
        assertEquals(AlertType.PAYMENT_PENDING, stored.getType());
        assertTrue(stored.isOpen());
    }
}