package lk.metrolink.journey.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import lk.metrolink.journey.domain.FareTransaction;
import lk.metrolink.journey.domain.Journey;
import lk.metrolink.journey.domain.QRTicket;
import lk.metrolink.journey.domain.TicketState;
import lk.metrolink.journey.repository.FareTransactionRepository;
import org.junit.Before;
import org.junit.Test;

/** Rules X1 to X8 and D2. */
public class ExitTest {

    private Fixture f;

    @Before
    public void setUp() {
        f = new Fixture();
    }

    @Test
    public void scanningOutWithoutScanningInIsRejected() {                                       // X1
        ScanResult result = f.out("MLK-HAPPY01", "S5", Fixture.LATER);

        assertEquals(ScanOutcome.REJECTED, result.getOutcome());
        assertEquals(RejectionReason.MISSING_BOARDING_SCAN, result.getReason());
        assertEquals("the ticket is known", "MLK-HAPPY01", result.getTicket().getQrCode());
        assertEquals(0, f.journeyCount());
        assertEquals(500, f.balanceOf(1));
    }

    @Test
    public void scanningOutWithAnUnknownTokenIsRejectedToo() {                                   // X1
        ScanResult result = f.out("NOT-A-TICKET", "S5", Fixture.LATER);

        assertEquals(RejectionReason.MISSING_BOARDING_SCAN, result.getReason());
        assertNull(result.getTicket());
    }

    @Test
    public void aFullJourneyChargesThePlannedFareOnceAndClosesEverything() {                     // X5
        f.in("MLK-HAPPY01", "S1", Fixture.MORNING);

        ScanResult result = f.out("MLK-HAPPY01", "S5", Fixture.LATER);

        assertEquals(ScanOutcome.JOURNEY_COMPLETED, result.getOutcome());
        assertEquals(Long.valueOf(69), result.getFareDue());
        assertEquals(Long.valueOf(69), result.getFareCharged());
        assertEquals(Long.valueOf(431), result.getBalance());
        assertFalse(result.isOvertravelled());

        Journey journey = f.journeyOf("MLK-HAPPY01");
        assertEquals("COMPLETED", journey.status());
        assertEquals("S5", journey.getExitStopId());
        assertEquals(Fixture.LATER, journey.getTapOutTime());
        assertEquals(Long.valueOf(69), journey.getFareDue());
        assertEquals(Long.valueOf(69), journey.getFareCharged());
        assertEquals("USED", f.ticket("MLK-HAPPY01").status());
        assertEquals(431, f.balanceOf(1));
        assertEquals(1, f.chargeCount(1));
    }

    @Test
    public void theChargeIsRecordedWithTheJourneyReference() {                                   // D2
        f.in("MLK-HAPPY01", "S1", Fixture.MORNING);
        f.out("MLK-HAPPY01", "S5", Fixture.LATER);
        long journeyId = f.journeyOf("MLK-HAPPY01").getJourneyId();

        FareTransaction charge = f.db.run(() ->
                f.transactions.findByReference("FARE-" + journeyId).orElseThrow());

        assertEquals(69, charge.getAmount());
        assertEquals(431, charge.getBalanceAfter());
        assertEquals(journeyId, charge.getJourneyId());
        assertEquals(Fixture.LATER, charge.getCreatedAt());
    }

    @Test
    public void theFareIsForTheStopWhereThePassengerActuallyGotOff() {                           // X3
        f.in("MLK-HAPPY01", "S1", Fixture.MORNING);

        ScanResult result = f.out("MLK-HAPPY01", "S4", Fixture.LATER);                          // planned S5

        assertEquals(Long.valueOf(54), result.getFareCharged());
        assertEquals(446, f.balanceOf(1));
        assertFalse("getting off early is not overtravel", result.isOvertravelled());
    }

    @Test
    public void aFlatFareRouteChargesTheFlatAmount() {                                           // X4
        f.in("MLK-FLAT001", "S1", Fixture.MORNING);

        ScanResult result = f.out("MLK-FLAT001", "S2", Fixture.LATER);

        assertEquals(Long.valueOf(30), result.getFareCharged());
    }

    @Test
    public void theFareUsesTheRuleInForceOnTheTapInDate() {                                      // X4
        LocalDate june30 = LocalDate.parse("2026-06-30");
        Instant tapIn = june30.atTime(9, 30).toInstant(ZoneOffset.ofHoursMinutes(5, 30));
        Instant tapOut = june30.atTime(10, 10).toInstant(ZoneOffset.ofHoursMinutes(5, 30));
        f.db.execute(() -> f.tickets.insert(new QRTicket(0, "MLK-JUNE30", 1, "R100", "S1", "S5", june30, 64,
                tapIn.minusSeconds(3600), TicketState.of("ACTIVE"))));

        f.in("MLK-JUNE30", "S1", tapIn);
        ScanResult result = f.out("MLK-JUNE30", "S5", tapOut);

        assertEquals("20 + 4.0 x 10.8 = 63.2, rounded up", Long.valueOf(64), result.getFareCharged());
    }

    @Test
    public void aJourneyThatCrossesMidnightIsPricedByTheTapInDate() {                            // X4
        ZoneOffset colombo = ZoneOffset.ofHoursMinutes(5, 30);
        LocalDate june30 = LocalDate.parse("2026-06-30");
        Instant tapIn = june30.atTime(23, 30).toInstant(colombo);
        Instant tapOut = june30.plusDays(1).atTime(0, 10).toInstant(colombo);                    // already 1 July
        f.db.execute(() -> f.tickets.insert(new QRTicket(0, "MLK-NIGHT01", 1, "R100", "S1", "S5", june30, 64,
                tapIn.minusSeconds(3600), TicketState.of("ACTIVE"))));

        f.in("MLK-NIGHT01", "S1", tapIn);
        ScanResult result = f.out("MLK-NIGHT01", "S5", tapOut);

        assertEquals("the old rate applies because the passenger tapped in on 30 June",
                Long.valueOf(64), result.getFareCharged());
    }

    @Test
    public void aWrongExitStopLeavesTheJourneyOpenAndChargesNothing() {                          // X2
        f.in("MLK-HAPPY01", "S2", Fixture.MORNING);

        ScanResult sameStop = f.out("MLK-HAPPY01", "S2", Fixture.LATER);
        ScanResult before = f.out("MLK-HAPPY01", "S1", Fixture.LATER);
        ScanResult unknown = f.out("MLK-HAPPY01", "NOWHERE", Fixture.LATER);

        for (ScanResult result : new ScanResult[] {sameStop, before, unknown}) {
            assertEquals(ScanOutcome.REJECTED, result.getOutcome());
            assertEquals(RejectionReason.INVALID_EXIT_STOP, result.getReason());
        }
        assertEquals("IN_PROGRESS", f.journeyOf("MLK-HAPPY01").status());
        assertNull(f.journeyOf("MLK-HAPPY01").getFareDue());
        assertNull(f.journeyOf("MLK-HAPPY01").getExitStopId());
        assertEquals(500, f.balanceOf(1));

        assertEquals("a right tap afterwards still works",
                ScanOutcome.JOURNEY_COMPLETED, f.out("MLK-HAPPY01", "S5", Fixture.LATER).getOutcome());
    }

    @Test
    public void anExitStopOnAnotherRouteIsInvalid() {                                            // X2
        f.in("MLK-FLAT001", "S1", Fixture.MORNING);

        ScanResult result = f.out("MLK-FLAT001", "S6", Fixture.LATER);                          // S6 is not on R200

        assertEquals(RejectionReason.INVALID_EXIT_STOP, result.getReason());
        assertEquals("IN_PROGRESS", f.journeyOf("MLK-FLAT001").status());
    }

    @Test
    public void notEnoughCreditAtTheExitLeavesTheFareOwedAndNothingDebited() {                   // X6
        f.in("MLK-PENDING", "S1", Fixture.MORNING);                                              // planned 40, balance 45

        ScanResult result = f.out("MLK-PENDING", "S5", Fixture.LATER);                          // really 69

        assertEquals(ScanOutcome.PAYMENT_PENDING, result.getOutcome());
        assertEquals(Long.valueOf(69), result.getFareDue());
        assertEquals(Long.valueOf(45), result.getBalance());
        assertEquals(Long.valueOf(24), result.getShortfall());
        assertNull(result.getFareCharged());

        Journey journey = f.journeyOf("MLK-PENDING");
        assertEquals("PAYMENT_PENDING", journey.status());
        assertEquals(Long.valueOf(69), journey.getFareDue());
        assertNull(journey.getFareCharged());
        assertEquals("S5", journey.getExitStopId());
        assertEquals("ACTIVE", f.ticket("MLK-PENDING").status());
        assertEquals(45, f.balanceOf(2));
        assertEquals(0, f.chargeCount(2));
    }

    @Test
    public void exitingPastThePlannedStopIsPricedToTheActualStopAndFlagged() {                   // X7
        f.in("MLK-OVERTR1", "S1", Fixture.MORNING);                                              // planned S4

        ScanResult result = f.out("MLK-OVERTR1", "S5", Fixture.LATER);

        assertEquals(ScanOutcome.JOURNEY_COMPLETED, result.getOutcome());
        assertTrue(result.isOvertravelled());
        assertEquals("priced to S5, not to the planned S4", Long.valueOf(69), result.getFareCharged());
        assertEquals("COMPLETED", f.journeyOf("MLK-OVERTR1").status());
        assertEquals(431, f.balanceOf(1));
    }

    @Test
    public void overtravelIsAFlagAndNeverStopsThePaymentFromBeingTaken() {                       // X7
        f.in("MLK-OVERTR1", "S1", Fixture.MORNING);

        ScanResult result = f.out("MLK-OVERTR1", "S5", Fixture.LATER);

        assertEquals(ScanOutcome.JOURNEY_COMPLETED, result.getOutcome());
        assertEquals("USED", f.ticket("MLK-OVERTR1").status());
    }

    @Test
    public void exitingAtThePlannedStopIsNotOvertravel() {                                       // X7
        f.in("MLK-OVERTR1", "S1", Fixture.MORNING);

        assertFalse(f.out("MLK-OVERTR1", "S4", Fixture.LATER).isOvertravelled());
    }

    @Test
    public void scanningOutAgainOnASettledJourneyChangesNothing() {                              // X8
        f.in("MLK-HAPPY01", "S1", Fixture.MORNING);
        f.out("MLK-HAPPY01", "S5", Fixture.LATER);

        ScanResult again = f.out("MLK-HAPPY01", "S5", Fixture.LATER.plusSeconds(5));

        assertEquals(ScanOutcome.ALREADY_SETTLED, again.getOutcome());
        assertEquals(Long.valueOf(69), again.getFareCharged());
        assertEquals(431, f.balanceOf(1));
        assertEquals(1, f.chargeCount(1));
        assertEquals("COMPLETED", f.journeyOf("MLK-HAPPY01").status());
    }

    @Test
    public void aSecondExitAtADifferentStopStillChangesNothing() {                               // X8
        f.in("MLK-HAPPY01", "S1", Fixture.MORNING);
        f.out("MLK-HAPPY01", "S5", Fixture.LATER);

        ScanResult again = f.out("MLK-HAPPY01", "S2", Fixture.LATER.plusSeconds(5));

        assertEquals(ScanOutcome.ALREADY_SETTLED, again.getOutcome());
        assertEquals("S5", f.journeyOf("MLK-HAPPY01").getExitStopId());
        assertEquals(431, f.balanceOf(1));
    }

    @Test
    public void aChargeThatAlreadyExistsForTheJourneyIsNeverRepeated() {                        // D2
        f.in("MLK-HAPPY01", "S1", Fixture.MORNING);
        Journey journey = f.journeyOf("MLK-HAPPY01");
        f.db.execute(() -> f.transactions.insert(
                FareTransaction.forJourney(journey.getJourneyId(), 1, 69, 431, Fixture.LATER)));

        ScanResult result = f.out("MLK-HAPPY01", "S5", Fixture.LATER);

        assertEquals(ScanOutcome.JOURNEY_COMPLETED, result.getOutcome());
        assertEquals("the balance was not touched a second time", 500, f.balanceOf(1));
        assertEquals(1, f.chargeCount(1));
    }

    @Test
    public void aJourneyThatWasAlreadyPaidCompletesEvenIfTheBalanceIsNowLow() {                  // D2
        f.in("MLK-PENDING", "S1", Fixture.MORNING);                                              // balance 45
        Journey journey = f.journeyOf("MLK-PENDING");
        f.db.execute(() -> {
            var account = f.accounts.findByPassenger(2).orElseThrow();
            long after = account.deduct(40, Fixture.LATER);                                      // 45 - 40 = 5
            f.accounts.save(account);
            f.transactions.insert(FareTransaction.forJourney(journey.getJourneyId(), 2, 40, after, Fixture.LATER));
        });

        ScanResult result = f.out("MLK-PENDING", "S3", Fixture.LATER);                          // the fare is 40

        assertEquals(ScanOutcome.JOURNEY_COMPLETED, result.getOutcome());
        assertEquals(5, f.balanceOf(2));
        assertEquals(1, f.chargeCount(2));
    }

    @Test
    public void twoExitScansAtTheSameMomentChargeOnce() throws Exception {                      // D2
        f.in("MLK-HAPPY01", "S1", Fixture.MORNING);

        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(6);
        java.util.List<java.util.concurrent.Future<ScanResult>> results = new java.util.ArrayList<>();
        for (int i = 0; i < 6; i++) {
            results.add(pool.submit(() -> f.out("MLK-HAPPY01", "S5", Fixture.LATER)));
        }
        long completed = 0;
        long settledAlready = 0;
        for (java.util.concurrent.Future<ScanResult> r : results) {
            ScanOutcome outcome = r.get().getOutcome();
            if (outcome == ScanOutcome.JOURNEY_COMPLETED) completed++;
            if (outcome == ScanOutcome.ALREADY_SETTLED) settledAlready++;
        }
        pool.shutdown();

        assertEquals(1, completed);
        assertEquals(5, settledAlready);
        assertEquals(431, f.balanceOf(1));
        assertEquals(1, f.chargeCount(1));
    }

    @Test
    public void ifThePaymentFailsHalfWayEverythingIsUndone() {                                   // D5
        Fixture failing = new Fixture(real -> new FareTransactionRepository() {
            @Override
            public Optional<FareTransaction> findByReference(String reference) {
                return real.findByReference(reference);
            }

            @Override
            public FareTransaction insert(FareTransaction draft) {
                throw new IllegalStateException("the disk is full");
            }

            @Override
            public List<FareTransaction> listForAccount(long accountId) {
                return real.listForAccount(accountId);
            }
        });
        failing.in("MLK-HAPPY01", "S1", Fixture.MORNING);

        try {
            failing.out("MLK-HAPPY01", "S5", Fixture.LATER);
            fail("expected the failure to reach the caller");
        } catch (IllegalStateException expected) {
            assertEquals("the disk is full", expected.getMessage());
        }

        assertEquals("the money was put back", 500, failing.balanceOf(1));
        Journey journey = failing.journeyOf("MLK-HAPPY01");
        assertEquals("IN_PROGRESS", journey.status());
        assertNull("the exit was not recorded either", journey.getExitStopId());
        assertNull(journey.getFareDue());
        assertEquals("ACTIVE", failing.ticket("MLK-HAPPY01").status());
    }
}
