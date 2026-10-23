package lk.metrolink.journey.repository;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.time.Instant;
import java.time.LocalDate;
import lk.metrolink.journey.domain.Account;
import lk.metrolink.journey.domain.Dates;
import lk.metrolink.journey.domain.Passenger;
import lk.metrolink.journey.domain.PasswordHash;
import lk.metrolink.journey.domain.QRTicket;
import lk.metrolink.journey.domain.Resident;
import lk.metrolink.journey.domain.Route;
import lk.metrolink.journey.fare.FareCalculator;
import org.junit.Before;
import org.junit.Test;

public class DemoDataTest extends OnEveryStore {

    private static final LocalDate TODAY = LocalDate.parse("2026-10-21");
    private static final Instant NOON = Instant.parse("2026-10-21T06:30:00Z");

    private Store store;
    private UnitOfWork db;
    private TicketRepository tickets;
    private AccountRepository accounts;
    private PassengerRepository passengers;
    private RouteRepository routes;
    private FareRuleRepository fareRules;
    private CredentialRepository credentials;

    @Before
    public void setUp() {
        store = open(TODAY);
        db = store.unitOfWork();
        tickets = store.tickets();
        accounts = store.accounts();
        passengers = store.passengers();
        routes = store.routes();
        fareRules = store.fareRules();
        credentials = store.credentials();
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
        Store real = open(Dates.today());

        assertEquals(10, real.unitOfWork().run(() -> real.tickets().list().size()).intValue());
    }


    @Test
    public void everyDemoPassengerHasAPasswordThatIsTheDemoPassword() {
        db.execute(() -> {
            for (Passenger passenger : passengers.list()) {
                PasswordHash hash = credentials.findByPassenger(passenger.getPassengerId()).orElseThrow();
                assertTrue(passenger.getEmail(), hash.matches("Demo@1234"));
                assertFalse(passenger.getEmail(), hash.matches("demo@1234"));
            }
        });
    }

    @Test
    public void aResetBringsBackTheDemoCustomersAndForgetsNewOnes() {
        db.execute(() -> passengers.insert(new Passenger(0, "Amaya", "amaya@example.com", new Resident())));
        assertEquals(6, db.run(() -> passengers.list()).size());

        store.reset(TODAY);

        assertEquals(5, db.run(() -> passengers.list()).size());
        assertTrue(db.run(() -> passengers.findByEmail("amaya@example.com")).isEmpty());
        assertEquals(6, db.run(() -> passengers.insert(new Passenger(0, "Binura", "binura@example.com",
                new Resident()))).getPassengerId());
    }
}
