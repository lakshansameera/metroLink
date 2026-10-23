package lk.metrolink.journey;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import java.time.Instant;
import lk.metrolink.journey.domain.FareTransaction;
import org.junit.Test;

public class FareTransactionTest {

    private static final Instant NOW = Instant.parse("2026-10-21T04:40:00Z");

    @Test
    public void theReferenceIsBuiltFromTheJourneyId() {                     // D2
        assertEquals("FARE-12", FareTransaction.referenceFor(12));
    }

    @Test
    public void oneJourneyAlwaysGivesTheSameReferenceAndDifferentJourneysDiffer() {   // D2
        assertEquals(FareTransaction.referenceFor(7), FareTransaction.referenceFor(7));
        assertNotEquals(FareTransaction.referenceFor(7), FareTransaction.referenceFor(8));
    }

    @Test
    public void aNewTransactionIsADraftWithTheJourneysReference() {
        FareTransaction draft = FareTransaction.forJourney(12, 3, 69, 431, NOW);

        assertEquals(0, draft.getTransactionId());
        assertEquals("FARE-12", draft.getReference());
        assertEquals(12, draft.getJourneyId());
        assertEquals(3, draft.getAccountId());
        assertEquals(69, draft.getAmount());
        assertEquals(431, draft.getBalanceAfter());
        assertEquals(NOW, draft.getCreatedAt());
    }

    @Test
    public void withIdGivesANewIdAndKeepsEverythingElse() {
        FareTransaction draft = FareTransaction.forJourney(12, 3, 69, 431, NOW);

        FareTransaction stored = draft.withId(40);

        assertEquals(40, stored.getTransactionId());
        assertEquals(0, draft.getTransactionId());
        assertEquals("FARE-12", stored.getReference());
        assertEquals(69, stored.getAmount());
        assertEquals(431, stored.getBalanceAfter());
        assertEquals(NOW, stored.getCreatedAt());
    }
}