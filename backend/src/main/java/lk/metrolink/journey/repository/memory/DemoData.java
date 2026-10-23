package lk.metrolink.journey.repository.memory;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import lk.metrolink.journey.domain.Account;
import lk.metrolink.journey.domain.Dates;
import lk.metrolink.journey.domain.FareRule;
import lk.metrolink.journey.domain.NonResident;
import lk.metrolink.journey.domain.Passenger;
import lk.metrolink.journey.domain.QRTicket;
import lk.metrolink.journey.domain.Residency;
import lk.metrolink.journey.domain.Resident;
import lk.metrolink.journey.domain.Route;
import lk.metrolink.journey.domain.RouteStop;
import lk.metrolink.journey.domain.Stop;
import lk.metrolink.journey.domain.TicketState;

/**
 * The data the database starts with.
 *
 * <p>The network (stops, routes, fare rules) is what the Transport Manager would maintain. The passengers,
 * accounts and tickets stand in for the results of other functions (Top Up Credit, Generate QR Ticket), which are
 * not part of this one. They are test data, not an implementation of those functions.
 */
final class DemoData {

    private DemoData() {
    }

    static void load(InMemoryDatabase.Tables t, LocalDate today) {
        network(t);
        passengersAndTickets(t, today);
    }

    private static void network(InMemoryDatabase.Tables t) {
        for (Stop s : List.of(
                new Stop("S1", "Colombo Fort", 6.9335, 79.85),
                new Stop("S2", "Maradana", 6.929, 79.865),
                new Stop("S3", "Dematagoda", 6.942, 79.877),
                new Stop("S4", "Peliyagoda", 6.96, 79.889),
                new Stop("S5", "Kiribathgoda", 6.978, 79.922),
                new Stop("S6", "Kadawatha", 7.001, 79.953),
                new Stop("S7", "Ganemulla", 7.072, 80.002))) {
            t.stops.put(s.getStopId(), s);
        }

        t.routes.put("R100", new Route("R100", "Colombo Fort - Ganemulla (distance fare)", List.of(
                new RouteStop("S1", 1, 0), new RouteStop("S2", 2, 2.1), new RouteStop("S3", 3, 4.3),
                new RouteStop("S4", 4, 7.4), new RouteStop("S5", 5, 10.8), new RouteStop("S6", 6, 14.5),
                new RouteStop("S7", 7, 22.0))));
        t.routes.put("R200", new Route("R200", "Fort City Circular (flat fare)", List.of(
                new RouteStop("S1", 1, 0), new RouteStop("S2", 2, 2.1), new RouteStop("S3", 3, 4.3))));

        // Two rules for R100 with different dates: a price change is a new rule, not a code change.
        put(t, FareRule.distance(1, "R100", 20, 4.0, LocalDate.parse("2025-01-01"), LocalDate.parse("2026-06-30")));
        put(t, FareRule.distance(2, "R100", 20, 4.5, LocalDate.parse("2026-07-01"), null));
        put(t, FareRule.flat(3, "R200", 30, LocalDate.parse("2025-01-01"), null));
    }

    private static void put(InMemoryDatabase.Tables t, FareRule rule) {
        t.fareRules.put(rule.getFareRuleId(), rule);
    }

    private static void passengersAndTickets(InMemoryDatabase.Tables t, LocalDate today) {
        Instant startOfToday = today.atStartOfDay(Dates.SERVICE_ZONE).toInstant();
        Instant issued = today.minusDays(1).atTime(18, 0).atZone(Dates.SERVICE_ZONE).toInstant();

        passenger(t, 1, "Nimal Perera", "nimal@example.com", new Resident(), 500, startOfToday);
        passenger(t, 2, "Sanduni Silva", "sanduni@example.com", new Resident(), 45, startOfToday);
        passenger(t, 3, "Kasun Fernando", "kasun@example.com", new Resident(), 10, startOfToday);

        // Tourists have a temporary account that stops working after the visa expires.
        LocalDate validVisa = today.plusDays(10);
        LocalDate expiredVisa = today.minusDays(1);
        passenger(t, 4, "Emma Walker", "emma@example.com", new NonResident(validVisa), 300,
                validVisa.atStartOfDay(Dates.SERVICE_ZONE).toInstant());
        passenger(t, 5, "Hiro Tanaka", "hiro@example.com", new NonResident(expiredVisa), 300,
                expiredVisa.atStartOfDay(Dates.SERVICE_ZONE).toInstant());

        // The quoted fares are what FareCalculator gives for the rules above (DemoDataTest checks it).
        ticket(t, "MLK-HAPPY01", 1, "R100", "S1", "S5", today, 69, issued, "ACTIVE");      // normal trip
        ticket(t, "MLK-OVERTR1", 1, "R100", "S1", "S4", today, 54, issued, "ACTIVE");      // exit beyond the planned stop
        ticket(t, "MLK-FLAT001", 1, "R200", "S1", "S3", today, 30, issued, "ACTIVE");      // flat fare
        ticket(t, "MLK-PENDING", 2, "R100", "S1", "S3", today, 40, issued, "ACTIVE");      // 45 credit: boards, then too little at the exit
        ticket(t, "MLK-NOFUNDS", 3, "R100", "S1", "S3", today, 40, issued, "ACTIVE");      // 10 credit: refused at boarding
        ticket(t, "MLK-OLD0001", 1, "R100", "S1", "S4", today.minusDays(1), 54, issued, "ACTIVE");  // yesterday's ticket
        ticket(t, "MLK-FUTURE1", 1, "R100", "S1", "S4", today.plusDays(1), 54, issued, "ACTIVE");   // tomorrow's ticket
        ticket(t, "MLK-CANCEL01", 1, "R100", "S1", "S4", today, 54, issued, "CANCELLED");  // cancelled
        ticket(t, "MLK-TOURIST1", 4, "R100", "S1", "S3", today, 40, issued, "ACTIVE");     // tourist, visa still valid
        ticket(t, "MLK-VISAEXP1", 5, "R100", "S1", "S3", today, 40, issued, "ACTIVE");     // tourist, visa has expired
    }

    private static void passenger(InMemoryDatabase.Tables t, long id, String name, String email, Residency residency,
                                  long balance, Instant fundedAt) {
        Passenger passenger = new Passenger(id, name, email, residency);
        t.passengers.put(id, passenger);
        Account account = passenger.openAccount(id);       // one account per passenger; same id keeps the demo readable
        account.credit(balance, fundedAt);
        t.accounts.put(id, account);
    }

    private static void ticket(InMemoryDatabase.Tables t, String qr, long passengerId, String routeNo, String from,
                               String to, LocalDate travelDate, long quotedFare, Instant issued, String state) {
        long id = t.nextTicketId++;
        t.tickets.put(id, new QRTicket(id, qr, passengerId, routeNo, from, to, travelDate, quotedFare, issued,
                TicketState.of(state)));
    }
}
