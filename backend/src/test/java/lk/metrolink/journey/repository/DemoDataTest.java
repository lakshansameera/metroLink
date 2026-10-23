package lk.metrolink.journey.repository;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.time.Instant;
import java.time.LocalDate;
import lk.metrolink.journey.domain.Account;
import lk.metrolink.journey.domain.Dates;
import lk.metrolink.journey.domain.QRTicket;
import lk.metrolink.journey.domain.Route;
import lk.metrolink.journey.fare.FareCalculator;
import lk.metrolink.journey.repository.memory.InMemoryAccountRepository;
import lk.metrolink.journey.repository.memory.InMemoryDatabase;
import lk.metrolink.journey.repository.memory.InMemoryFareRuleRepository;
import lk.metrolink.journey.repository.memory.InMemoryPassengerRepository;
import lk.metrolink.journey.repository.memory.InMemoryRouteRepository;
import lk.metrolink.journey.repository.memory.InMemoryTicketRepository;
import org.junit.Before;
import org.junit.Test;

public class DemoDataTest {

    private static final LocalDate TODAY = LocalDate.parse("2026-10-21");
    private static final Instant NOON = Instant.parse("2026-10-21T06:30:00Z");

    private InMemoryDatabase db;
    private TicketRepository tickets;
    private AccountRepository accounts;
    private PassengerRepository passengers;
    private RouteRepository routes;
    private FareRuleRepository fareRules;

    @Before
    public void setUp() {
        db = InMemoryDatabase.seeded(TODAY);
        tickets = new InMemoryTicketRepository(db);
        accounts = new InMemoryAccountRepository(db);
        passengers = new InMemoryPassengerRepository(db);
        routes = new InMemoryRouteRepository(db);
        fareRules = new InMemoryFareRuleRepository(db);
    }

    @Test
    public void everyQuotedFareIsWhatTheCalculatorGivesForTheSeededRules() {
        FareCalculator calculator = new FareCalculator();

        db.execute(() -> {
            for (QRTicket ticket : tickets.list()) {
                Route route = routes.findRoute(ticket.getRouteNo()).orElseThrow();
                long quote = calculator.calculate(route, fareRules.listForRoute(ticket.getRouteNo()),
                        ticket.getBoardingStopId(), ticket.getPlannedDestinationStopId(), TODAY).getAmount();
                assertEquals(ticket.getQrCode(), quote, ticket.getQuotedFare());
            }
        });
    }

    @Test
    public void everyTicketRefersToARealPassengerRouteAndStops() {
        db.execute(() -> {
            for (QRTicket ticket : tickets.list()) {
                assertTrue(passengers.find(ticket.getPassengerId()).isPresent());
                Route route = routes.findRoute(ticket.getRouteNo()).orElseThrow();
                assertTrue(route.hasStop(ticket.getBoardingStopId()));
                assertTrue(route.isBefore(ticket.getBoardingStopId(), ticket.getPlannedDestinationStopId()));
            }
        });
    }

    @Test
    public void residentsGetPermanentAccountsAndTouristsGetTemporaryOnes() {
        db.execute(() -> {
            assertEquals("PERMANENT", accounts.findByPassenger(1).orElseThrow().type());
            assertEquals("TEMPORARY", accounts.findByPassenger(4).orElseThrow().type());
            assertEquals("TEMPORARY", accounts.findByPassenger(5).orElseThrow().type());
        });
    }

    @Test
    public void theTouristWithAValidVisaCanPayAndTheOneWithAnExpiredVisaCannot() {
        db.execute(() -> {
            Account valid = accounts.findByPassenger(4).orElseThrow();
            Account expired = accounts.findByPassenger(5).orElseThrow();

            assertTrue(valid.canPay(40, NOON));
            assertTrue("it has the credit but the visa is over", expired.hasSufficientCredit(40));
            assertFalse(expired.canPay(40, NOON));
        });
    }

    @Test
    public void openingBalancesAreWhatTheScenariosNeed() {
        db.execute(() -> {
            assertEquals(500, accounts.findByPassenger(1).orElseThrow().getBalance());
            assertEquals(45, accounts.findByPassenger(2).orElseThrow().getBalance());
            assertEquals(10, accounts.findByPassenger(3).orElseThrow().getBalance());
        });
    }

    @Test
    public void ticketsAreDatedRelativeToTheDayTheDatabaseWasSeededFor() {
        db.execute(() -> {
            assertEquals(TODAY, tickets.findByQrCode("MLK-HAPPY01").orElseThrow().getTravelDate());
            assertEquals(TODAY.minusDays(1), tickets.findByQrCode("MLK-OLD0001").orElseThrow().getTravelDate());
            assertEquals(TODAY.plusDays(1), tickets.findByQrCode("MLK-FUTURE1").orElseThrow().getTravelDate());
            assertEquals("CANCELLED", tickets.findByQrCode("MLK-CANCEL01").orElseThrow().status());
        });
    }

    @Test
    public void todayInColomboIsAValidSeedDay() {
        InMemoryDatabase real = InMemoryDatabase.seeded(Dates.today());

        assertEquals(10, real.run(() -> new InMemoryTicketRepository(real).list().size()).intValue());
    }
}
