package lk.metrolink.journey.service;

import java.util.ArrayList;
import java.util.List;
import lk.metrolink.journey.domain.Account;
import lk.metrolink.journey.domain.Journey;
import lk.metrolink.journey.domain.NotFoundException;
import lk.metrolink.journey.domain.Passenger;
import lk.metrolink.journey.domain.QRTicket;
import lk.metrolink.journey.domain.Route;
import lk.metrolink.journey.domain.RouteStop;
import lk.metrolink.journey.domain.Stop;
import lk.metrolink.journey.domain.TemporaryAccount;
import lk.metrolink.journey.repository.AccountRepository;
import lk.metrolink.journey.repository.FareTransactionRepository;
import lk.metrolink.journey.repository.JourneyRepository;
import lk.metrolink.journey.repository.PassengerRepository;
import lk.metrolink.journey.repository.RouteRepository;
import lk.metrolink.journey.repository.TicketRepository;
import lk.metrolink.journey.repository.UnitOfWork;
import lk.metrolink.journey.service.view.AccountStatement;
import lk.metrolink.journey.service.view.ChargeView;
import lk.metrolink.journey.service.view.JourneyView;
import lk.metrolink.journey.service.view.PassengerView;
import lk.metrolink.journey.service.view.ReferenceView;
import lk.metrolink.journey.service.view.TicketView;

/**
 * Everything the screens read. It changes nothing; it only turns domain objects into views that carry names next
 * to ids. Each public method is its own unit of work, so a screen never sees half a scan.
 */
public final class QueryService {
    private final UnitOfWork uow;
    private final PassengerRepository passengers;
    private final AccountRepository accounts;
    private final TicketRepository tickets;
    private final JourneyRepository journeys;
    private final FareTransactionRepository transactions;
    private final RouteRepository routes;

    public QueryService(UnitOfWork uow, PassengerRepository passengers, AccountRepository accounts,
                        TicketRepository tickets, JourneyRepository journeys, FareTransactionRepository transactions,
                        RouteRepository routes) {
        this.uow = uow;
        this.passengers = passengers;
        this.accounts = accounts;
        this.tickets = tickets;
        this.journeys = journeys;
        this.transactions = transactions;
        this.routes = routes;
    }

    public ReferenceView reference() {
        return uow.run(() -> {
            List<ReferenceView.StopView> stops = new ArrayList<>();
            for (Stop s : routes.listStops()) {
                stops.add(new ReferenceView.StopView(s.getStopId(), s.getName()));
            }
            List<ReferenceView.RouteView> routeViews = new ArrayList<>();
            for (Route r : routes.listRoutes()) {
                List<ReferenceView.RouteStopView> onRoute = new ArrayList<>();
                for (RouteStop rs : r.getStops()) {
                    onRoute.add(new ReferenceView.RouteStopView(rs.getStopId(), stopName(rs.getStopId()),
                            rs.getSequenceNo(), rs.getDistanceKm()));
                }
                routeViews.add(new ReferenceView.RouteView(r.getRouteNo(), r.getName(), onRoute));
            }
            return new ReferenceView(stops, routeViews);
        });
    }

    public List<PassengerView> passengers() {
        return uow.run(() -> passengers.list().stream().map(this::passengerView).toList());
    }

    public PassengerView passenger(long passengerId) {
        return uow.run(() -> passengerView(requirePassenger(passengerId)));
    }

    public AccountStatement statement(long passengerId) {
        return uow.run(() -> {
            Passenger passenger = requirePassenger(passengerId);
            Account account = requireAccount(passengerId);
            List<ChargeView> charges = transactions.listForAccount(account.getAccountId()).stream()
                    .map(t -> new ChargeView(t.getTransactionId(), t.getReference(), t.getJourneyId(), t.getAmount(),
                            t.getBalanceAfter(), t.getCreatedAt()))
                    .toList();
            return new AccountStatement(passengerView(passenger), charges);
        });
    }

    public List<TicketView> tickets() {
        return uow.run(() -> tickets.list().stream().map(this::ticketView).toList());
    }

    public List<JourneyView> journeys() {
        return uow.run(() -> journeys.list().stream().map(this::journeyView).toList());
    }

    public JourneyView journey(long journeyId) {
        return uow.run(() -> journeyView(
                journeys.findById(journeyId).orElseThrow(() -> new NotFoundException("Journey " + journeyId))));
    }

    // Used by the reader while it is already inside its own unit of work.

    TicketView ticketView(QRTicket ticket) {
        return uow.run(() -> new TicketView(ticket.getTicketId(), ticket.getQrCode(), ticket.getPassengerId(),
                passengerName(ticket.getPassengerId()), ticket.getRouteNo(),
                ticket.getBoardingStopId(), stopName(ticket.getBoardingStopId()),
                ticket.getPlannedDestinationStopId(), stopName(ticket.getPlannedDestinationStopId()),
                ticket.getTravelDate(), ticket.getQuotedFare(), ticket.status(), ticket.getIssuedAt()));
    }

    JourneyView journeyView(Journey journey) {
        return uow.run(() -> {
            QRTicket ticket = tickets.findById(journey.getTicketId())
                    .orElseThrow(() -> new NotFoundException("Ticket " + journey.getTicketId()));
            Route route = routes.findRoute(journey.getRouteNo())
                    .orElseThrow(() -> new NotFoundException("Route " + journey.getRouteNo()));
            String exitStopId = journey.getExitStopId();
            boolean overtravelled = exitStopId != null
                    && route.isBefore(ticket.getPlannedDestinationStopId(), exitStopId);
            return new JourneyView(journey.getJourneyId(), journey.getTicketId(), ticket.getQrCode(),
                    ticket.getPassengerId(), passengerName(ticket.getPassengerId()), journey.getRouteNo(),
                    journey.getBoardingStopId(), stopName(journey.getBoardingStopId()),
                    exitStopId, exitStopId == null ? null : stopName(exitStopId),
                    ticket.getPlannedDestinationStopId(), journey.getTapInTime(), journey.getTapOutTime(),
                    journey.getFareDue(), journey.getFareCharged(), journey.status(), overtravelled);
        });
    }

    private PassengerView passengerView(Passenger passenger) {
        Account account = requireAccount(passenger.getPassengerId());
        return new PassengerView(passenger.getPassengerId(), passenger.getName(), passenger.getEmail(),
                passenger.getResidency().name(), account.getAccountId(), account.type(), account.getBalance(),
                account instanceof TemporaryAccount temporary ? temporary.getVisaExpiry() : null);
    }

    private Passenger requirePassenger(long passengerId) {
        return passengers.find(passengerId)
                .orElseThrow(() -> new NotFoundException("Passenger " + passengerId));
    }

    private Account requireAccount(long passengerId) {
        return accounts.findByPassenger(passengerId)
                .orElseThrow(() -> new NotFoundException("Account for passenger " + passengerId));
    }

    private String passengerName(long passengerId) {
        return passengers.find(passengerId).map(Passenger::getName).orElse("Passenger " + passengerId);
    }

    private String stopName(String stopId) {
        return routes.findStop(stopId).map(Stop::getName).orElse(stopId);
    }
}
