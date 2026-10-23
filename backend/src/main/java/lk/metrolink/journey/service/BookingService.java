package lk.metrolink.journey.service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import lk.metrolink.journey.domain.Account;
import lk.metrolink.journey.domain.BookingRequest;
import lk.metrolink.journey.domain.Dates;
import lk.metrolink.journey.domain.InvalidBookingException;
import lk.metrolink.journey.domain.NotFoundException;
import lk.metrolink.journey.domain.QRTicket;
import lk.metrolink.journey.domain.Route;
import lk.metrolink.journey.fare.FareCalculator;
import lk.metrolink.journey.fare.FareQuote;
import lk.metrolink.journey.log.Log;
import lk.metrolink.journey.repository.AccountRepository;
import lk.metrolink.journey.repository.FareRuleRepository;
import lk.metrolink.journey.repository.RouteRepository;
import lk.metrolink.journey.repository.TicketRepository;
import lk.metrolink.journey.repository.UnitOfWork;
import lk.metrolink.journey.service.view.BookingResult;
import lk.metrolink.journey.service.view.TicketQuote;
import lk.metrolink.journey.service.view.TicketView;

/**
 * Ticket booking: how a ticket comes to exist (rules T1 to T12).
 *
 * <p>This class only coordinates, as the other services do. The request knows what is acceptable, the fare calculator
 * prices the trip (the same one the exit uses, so a quote and the fare charged agree), and the ticket is created in
 * the state the State pattern calls Active. A booking is one unit of work, so a ticket is stored completely or not
 * at all. Nothing here moves money: the fare is only quoted, and is charged at the exit.
 */
public final class BookingService {

    private static final int QR_ATTEMPTS = 5;
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);

    private final UnitOfWork uow;
    private final AccountRepository accounts;
    private final TicketRepository tickets;
    private final RouteRepository routes;
    private final FareRuleRepository fareRules;
    private final FareCalculator calculator;
    private final QueryService queries;
    private final Supplier<String> qrCodes;

    public BookingService(UnitOfWork uow, AccountRepository accounts, TicketRepository tickets,
                          RouteRepository routes, FareRuleRepository fareRules, FareCalculator calculator,
                          QueryService queries, Supplier<String> qrCodes) {
        this.uow = uow;
        this.accounts = accounts;
        this.tickets = tickets;
        this.routes = routes;
        this.fareRules = fareRules;
        this.calculator = calculator;
        this.queries = queries;
        this.qrCodes = qrCodes;
    }

    /** What the trip would cost, after the same checks as a booking. Creates nothing (rule T11). */
    public TicketQuote quote(BookingRequest request) {
        return quote(request, Dates.now());
    }

    /** {@code at} is the moment of asking; its Colombo date is "today" for the checks. */
    public TicketQuote quote(BookingRequest request, Instant at) {
        return uow.run(() -> {
            Log.info(Log.SERVICE, "BookingService.quote: passenger #%d, route %s %s to %s on %s (nothing is created)",
                    request.getPassengerId(), request.getRouteNo(), request.getBoardingStopId(),
                    request.getDestinationStopId(), request.getTravelDate());
            TicketQuote quote = checked(request, at).quote;
            Log.info(Log.SERVICE, "BookingService.quote: fare LKR %d, balance LKR %d, can pay now: %b", quote.getFare(),
                    quote.getBalance(), quote.isCanPayNow());
            return quote;
        });
    }

    public BookingResult book(BookingRequest request) {
        return book(request, Dates.now());
    }

    public BookingResult book(BookingRequest request, Instant at) {
        return uow.run(() -> {
            Log.info(Log.SERVICE, "BookingService.book: passenger #%d, route %s %s to %s on %s", request.getPassengerId(),
                    request.getRouteNo(), request.getBoardingStopId(), request.getDestinationStopId(),
                    request.getTravelDate());
            Checked checked = checked(request, at);                                              // T1 to T6
            QRTicket ticket = tickets.insert(QRTicket.issue(freshQrCode(), request.getPassengerId(),     // T7, T8, T9
                    request.getRouteNo(), request.getBoardingStopId(), request.getDestinationStopId(),
                    request.getTravelDate(), checked.quote.getFare(), at));
            TicketView view = queries.ticketView(ticket);
            Log.info(Log.SERVICE, "BookingService.book: ticket #%d %s stored as %s. No money moves; the fare of LKR %d is"
                    + " charged at the exit", view.getTicketId(), view.getQrCode(), view.getStatus(), view.getQuotedFare());
            return new BookingResult(view, message(view, checked.quote), checked.quote.getBalance(),
                    checked.quote.isCanPayNow());                                                       // T10
        });
    }

    /** The facts behind an acceptable request. Runs inside a unit of work. */
    private Checked checked(BookingRequest request, Instant at) {
        Account account = accounts.findByPassenger(request.getPassengerId())
                .orElseThrow(() -> new NotFoundException("Passenger " + request.getPassengerId()));         // T5
        Route route = request.getRouteNo() == null || request.getRouteNo().isEmpty()
                ? null
                : routes.findRoute(request.getRouteNo()).orElse(null);

        List<BookingRequest.Problem> problems = request.problems(route, account, Dates.localDate(at));    // T1 to T4
        if (!problems.isEmpty()) {
            Log.info(Log.SERVICE, "BookingRequest.problems: refused, %d wrong field(s): %s", problems.size(),
                    problems.stream().map(BookingRequest.Problem::getField).collect(Collectors.joining(", ")));
            throw new InvalidBookingException(problems);
        }

        FareQuote fare = calculator.calculate(route, fareRules.listForRoute(route.getRouteNo()),
                request.getBoardingStopId(), request.getDestinationStopId(), request.getTravelDate());   // T6
        TicketQuote quote = new TicketQuote(fare.getAmount(), fare.getDistanceKm(), fare.getFareType().name(),
                account.getBalance(), account.canPay(fare.getAmount(), at));
        return new Checked(quote);
    }

    /** A code nobody has. The store also refuses a duplicate, so this only saves a failed booking (rule T8). */
    private String freshQrCode() {
        for (int attempt = 0; attempt < QR_ATTEMPTS; attempt++) {
            String code = qrCodes.get();
            if (tickets.findByQrCode(code).isEmpty()) {
                return code;
            }
            Log.info(Log.SERVICE, "BookingService: QR code %s is already taken, drawing another (try %d of %d)", code,
                    attempt + 1, QR_ATTEMPTS);
        }
        throw new IllegalStateException("Could not find an unused QR code after " + QR_ATTEMPTS + " tries");
    }

    private static String message(TicketView ticket, TicketQuote quote) {
        String trip = ticket.getBoardingStopName() + " to " + ticket.getPlannedDestinationStopName() + " on "
                + DAY.format(ticket.getTravelDate());
        String sentence = "Ticket booked: " + trip + ". The fare is about LKR " + quote.getFare()
                + ", charged when you get off.";
        if (!quote.isCanPayNow()) {
            sentence += " Your balance is LKR " + quote.getBalance() + ", so add credit before you tap in.";
        }
        return sentence;
    }

    private static final class Checked {
        private final TicketQuote quote;

        private Checked(TicketQuote quote) {
            this.quote = quote;
        }
    }
}
