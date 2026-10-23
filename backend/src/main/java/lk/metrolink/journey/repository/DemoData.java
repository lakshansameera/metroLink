package lk.metrolink.journey.repository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import lk.metrolink.journey.domain.Dates;
import lk.metrolink.journey.domain.FareRule;
import lk.metrolink.journey.domain.NonResident;
import lk.metrolink.journey.domain.Passenger;
import lk.metrolink.journey.domain.PasswordHash;
import lk.metrolink.journey.domain.QRTicket;
import lk.metrolink.journey.domain.Residency;
import lk.metrolink.journey.domain.Resident;
import lk.metrolink.journey.domain.Route;
import lk.metrolink.journey.domain.RouteStop;
import lk.metrolink.journey.domain.Stop;
import lk.metrolink.journey.domain.TicketState;

/**
 * The data a store starts with, written once and used by every store. This class only describes the data; each store
 * decides how to keep it.
 *
 * <p>The network (stops, routes, fare rules) is what the Transport Manager would maintain. The customers and tickets
 * stand in for the results of other functions (Top Up Credit, Generate QR Ticket), which are not part of this one.
 * They are test data, not an implementation of those functions.
 *
 * <p>Ids: customers are numbered 1 to 5 and their accounts get the same numbers. Tickets are numbered 1 to 10 in the
 * order of {@link #tickets}. A store that hands out ids in order from 1 therefore reproduces them exactly.
 */
public final class DemoData {

    /** Every demo customer logs in with this password. It is demo data, so it is written here on purpose. */
    public static final String DEMO_PASSWORD = "Demo@1234";

    /** A small number of repeats keeps demo data and tests fast. Real registrations use the full default. */
    private static final int DEMO_HASH_ITERATIONS = 1_000;

    /** A demo customer, the credit their account starts with, and when it was funded. */
    public record Customer(Passenger passenger, long balance, Instant fundedAt) {

        /** A fresh hash of the demo password. Each call uses a new random salt. */
        public PasswordHash passwordHash() {
            return PasswordHash.of(DEMO_PASSWORD, DEMO_HASH_ITERATIONS);
        }
    }

    private DemoData() {
    }

    public static List<Stop> stops() {
        return List.of(
                new Stop("S1", "Colombo Fort", 6.9335, 79.85),
                new Stop("S2", "Maradana", 6.929, 79.865),
                new Stop("S3", "Dematagoda", 6.942, 79.877),
                new Stop("S4", "Peliyagoda", 6.96, 79.889),
                new Stop("S5", "Kiribathgoda", 6.978, 79.922),
                new Stop("S6", "Kadawatha", 7.001, 79.953),
                new Stop("S7", "Ganemulla", 7.072, 80.002));
    }

    public static List<Route> routes() {
        return List.of(
                new Route("R100", "Colombo Fort - Ganemulla (distance fare)", List.of(
                        new RouteStop("S1", 1, 0), new RouteStop("S2", 2, 2.1), new RouteStop("S3", 3, 4.3),
                        new RouteStop("S4", 4, 7.4), new RouteStop("S5", 5, 10.8), new RouteStop("S6", 6, 14.5),
                        new RouteStop("S7", 7, 22.0))),
                new Route("R200", "Fort City Circular (flat fare)", List.of(
                        new RouteStop("S1", 1, 0), new RouteStop("S2", 2, 2.1), new RouteStop("S3", 3, 4.3))));
    }

    /** Two rules for R100 with different dates: a price change is a new rule, not a code change. */
    public static List<FareRule> fareRules() {
        return List.of(
                FareRule.distance(1, "R100", 20, 4.0, LocalDate.parse("2025-01-01"), LocalDate.parse("2026-06-30")),
                FareRule.distance(2, "R100", 20, 4.5, LocalDate.parse("2026-07-01"), null),
                FareRule.flat(3, "R200", 30, LocalDate.parse("2025-01-01"), null));
    }

    public static List<Customer> customers(LocalDate today) {
        Instant startOfToday = today.atStartOfDay(Dates.SERVICE_ZONE).toInstant();

        // Tourists have a temporary account that stops working after the visa expires.
        LocalDate validVisa = today.plusDays(10);
        LocalDate expiredVisa = today.minusDays(1);

        return List.of(
                customer(1, "Nimal Perera", "nimal@example.com", new Resident(), 500, startOfToday),
                customer(2, "Sanduni Silva", "sanduni@example.com", new Resident(), 45, startOfToday),
                customer(3, "Kasun Fernando", "kasun@example.com", new Resident(), 10, startOfToday),
                customer(4, "Emma Walker", "emma@example.com", new NonResident(validVisa), 300,
                        validVisa.atStartOfDay(Dates.SERVICE_ZONE).toInstant()),
                customer(5, "Hiro Tanaka", "hiro@example.com", new NonResident(expiredVisa), 300,
                        expiredVisa.atStartOfDay(Dates.SERVICE_ZONE).toInstant()));
    }

    /** New tickets, not yet stored (id 0). The quoted fares are what FareCalculator gives for the rules above. */
    public static List<QRTicket> tickets(LocalDate today) {
        Instant issued = today.minusDays(1).atTime(18, 0).atZone(Dates.SERVICE_ZONE).toInstant();

        return List.of(
                ticket("MLK-HAPPY01", 1, "R100", "S1", "S5", today, 69, issued, "ACTIVE"),      // normal trip
                ticket("MLK-OVERTR1", 1, "R100", "S1", "S4", today, 54, issued, "ACTIVE"),      // exit beyond the planned stop
                ticket("MLK-FLAT001", 1, "R200", "S1", "S3", today, 30, issued, "ACTIVE"),      // flat fare
                ticket("MLK-PENDING", 2, "R100", "S1", "S3", today, 40, issued, "ACTIVE"),      // 45 credit: boards, then too little at the exit
                ticket("MLK-NOFUNDS", 3, "R100", "S1", "S3", today, 40, issued, "ACTIVE"),      // 10 credit: refused at boarding
                ticket("MLK-OLD0001", 1, "R100", "S1", "S4", today.minusDays(1), 54, issued, "ACTIVE"),  // yesterday's ticket
                ticket("MLK-FUTURE1", 1, "R100", "S1", "S4", today.plusDays(1), 54, issued, "ACTIVE"),   // tomorrow's ticket
                ticket("MLK-CANCEL01", 1, "R100", "S1", "S4", today, 54, issued, "CANCELLED"),  // cancelled
                ticket("MLK-TOURIST1", 4, "R100", "S1", "S3", today, 40, issued, "ACTIVE"),     // tourist, visa still valid
                ticket("MLK-VISAEXP1", 5, "R100", "S1", "S3", today, 40, issued, "ACTIVE"));    // tourist, visa has expired
    }

    private static Customer customer(long id, String name, String email, Residency residency, long balance,
                                     Instant fundedAt) {
        return new Customer(new Passenger(id, name, email, residency), balance, fundedAt);
    }

    private static QRTicket ticket(String qr, long passengerId, String routeNo, String from, String to,
                                   LocalDate travelDate, long quotedFare, Instant issued, String state) {
        return new QRTicket(0, qr, passengerId, routeNo, from, to, travelDate, quotedFare, issued,
                TicketState.of(state));
    }
}
