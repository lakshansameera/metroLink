package lk.metrolink.journey.service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import lk.metrolink.journey.domain.Account;
import lk.metrolink.journey.domain.Dates;
import lk.metrolink.journey.domain.DomainException;
import lk.metrolink.journey.domain.FareTransaction;
import lk.metrolink.journey.domain.Journey;
import lk.metrolink.journey.domain.NotFoundException;
import lk.metrolink.journey.domain.QRTicket;
import lk.metrolink.journey.domain.Route;
import lk.metrolink.journey.fare.FareCalculator;
import lk.metrolink.journey.fare.FareQuote;
import lk.metrolink.journey.repository.FareRuleRepository;
import lk.metrolink.journey.repository.JourneyRepository;
import lk.metrolink.journey.repository.RouteRepository;
import lk.metrolink.journey.repository.TicketRepository;
import lk.metrolink.journey.repository.UnitOfWork;

/**
 * The Journey Execution workflow: scan in, scan out, settle.
 *
 * <p>This class only coordinates. The rules live in the objects it asks: the ticket says whether it is usable, the
 * route knows its stops and distances, the calculator prices, the account says whether it can pay, and the journey
 * moves between its states. Every public method is one unit of work, so a scan either happens completely or not at
 * all (rule D5).
 *
 * <pre>
 *   board():  validate ticket -> stop on route -> price planned trip -> can the account pay?
 *             -> open an IN_PROGRESS journey (no money moves)
 *   alight(): find the journey -> price to the stop actually used -> record the exit
 *             -> pay (COMPLETED, ticket USED) or wait for payment (PAYMENT_PENDING)
 *   settle(): try to pay a PAYMENT_PENDING journey again
 * </pre>
 */
public final class JourneyService {

    private final UnitOfWork uow;
    private final TicketRepository tickets;
    private final JourneyRepository journeys;
    private final RouteRepository routes;
    private final FareRuleRepository fareRules;
    private final QRTicketService ticketService;
    private final AccountService accountService;
    private final FareCalculator fares;

    public JourneyService(UnitOfWork uow, TicketRepository tickets, JourneyRepository journeys,
                          RouteRepository routes, FareRuleRepository fareRules, QRTicketService ticketService,
                          AccountService accountService, FareCalculator fares) {
        this.uow = uow;
        this.tickets = tickets;
        this.journeys = journeys;
        this.routes = routes;
        this.fareRules = fareRules;
        this.ticketService = ticketService;
        this.accountService = accountService;
        this.fares = fares;
    }

    // ------------------------------------------------------------------ boarding

    public ScanResult board(Scan scan) {
        return uow.run(() -> boardInUnit(scan));
    }

    private ScanResult boardInUnit(Scan scan) {
        Instant at = scan.getScannedAt();
        LocalDate today = Dates.localDate(at);

        TicketCheck check = ticketService.validate(scan.getQrCode(), today);               // B1, B2, B3
        if (!check.isValid()) {
            return ScanResult.of(ScanOutcome.REJECTED)
                    .reason(RejectionReason.from(check.getReason()))
                    .ticket(check.getTicket())
                    .build();
        }
        QRTicket ticket = check.getTicket();
        Route route = requireRoute(ticket.getRouteNo());

        if (!route.isBefore(scan.getStopId(), ticket.getPlannedDestinationStopId())) {      // B4
            return ScanResult.of(ScanOutcome.REJECTED)
                    .reason(RejectionReason.INVALID_BOARDING_STOP)
                    .ticket(ticket)
                    .build();
        }

        FareQuote planned = fares.calculate(route, fareRules.listForRoute(route.getRouteNo()),
                scan.getStopId(), ticket.getPlannedDestinationStopId(), today);
        Account account = accountService.requireForPassenger(ticket.getPassengerId());

        if (!account.canPay(planned.getAmount(), at)) {                                     // B5, A4
            return refusedAtBoarding(account, ticket, planned.getAmount(), at);
        }

        Journey journey = journeys.insert(
                Journey.start(ticket.getTicketId(), ticket.getRouteNo(), scan.getStopId(), at));   // B6
        return ScanResult.of(ScanOutcome.JOURNEY_STARTED)
                .journey(journey)
                .ticket(ticket)
                .required(planned.getAmount())
                .balance(account.getBalance())
                .build();
    }

    /** The account cannot pay, either because it has expired or because the balance is too low. */
    private ScanResult refusedAtBoarding(Account account, QRTicket ticket, long required, Instant at) {
        ScanResult.Builder refusal = ScanResult.of(ScanOutcome.REJECTED)
                .ticket(ticket)
                .required(required)
                .balance(account.getBalance());
        if (!account.isUsableAt(at)) {
            return refusal.reason(RejectionReason.ACCOUNT_EXPIRED).build();
        }
        return refusal.reason(RejectionReason.INSUFFICIENT_CREDIT)
                .shortfall(required - account.getBalance())
                .build();
    }

    // ------------------------------------------------------------------ exit

    public ScanResult alight(Scan scan) {
        return uow.run(() -> alightInUnit(scan));
    }

    private ScanResult alightInUnit(Scan scan) {
        Instant at = scan.getScannedAt();

        Optional<QRTicket> foundTicket = tickets.findByQrCode(scan.getQrCode());
        Optional<Journey> foundJourney = foundTicket.flatMap(t -> journeys.findByTicketId(t.getTicketId()));
        if (foundJourney.isEmpty()) {                                                       // X1
            return ScanResult.of(ScanOutcome.REJECTED)
                    .reason(RejectionReason.MISSING_BOARDING_SCAN)
                    .ticket(foundTicket.orElse(null))
                    .build();
        }
        QRTicket ticket = foundTicket.get();
        Journey journey = foundJourney.get();

        if (journey.isSettled()) {                                                          // X8
            return alreadySettled(journey, ticket);
        }
        if (journey.isAwaitingPayment()) {
            // Scanning out again is a retry of the fare that was already worked out.
            return attemptSettlement(journey, ticket, at, false);
        }

        Route route = requireRoute(journey.getRouteNo());
        FareQuote quote;
        try {
            quote = fares.calculate(route, fareRules.listForRoute(route.getRouteNo()),
                    journey.getBoardingStopId(), scan.getStopId(),
                    Dates.localDate(journey.getTapInTime()));                              // X2, X3, X4
        } catch (DomainException e) {
            if (e.code().equals("STOP_NOT_ON_ROUTE") || e.code().equals("EXIT_NOT_AFTER_BOARDING")) {
                // The journey stays open. A wrong tap is not a penalty.
                return ScanResult.of(ScanOutcome.REJECTED)
                        .reason(RejectionReason.INVALID_EXIT_STOP)
                        .detail(e.getMessage())
                        .journey(journey)
                        .ticket(ticket)
                        .build();
            }
            throw e;
        }

        journey.recordExit(scan.getStopId(), at, quote.getAmount());
        journeys.save(journey);

        boolean overtravelled = route.isBefore(ticket.getPlannedDestinationStopId(), scan.getStopId());   // X7
        return attemptSettlement(journey, ticket, at, overtravelled);
    }

    // ------------------------------------------------------------------ settling

    /** Tries to pay a PAYMENT_PENDING journey, for example after the passenger topped up (rule D1). */
    public ScanResult settle(long journeyId, Instant at) {
        return uow.run(() -> {
            Journey journey = journeys.findById(journeyId)
                    .orElseThrow(() -> new NotFoundException("Journey " + journeyId));
            QRTicket ticket = tickets.findById(journey.getTicketId())
                    .orElseThrow(() -> new NotFoundException("Ticket " + journey.getTicketId()));
            if (journey.isSettled()) {
                return alreadySettled(journey, ticket);
            }
            if (journey.isOpen()) {
                throw new DomainException("Journey " + journeyId + " has not ended yet; scan out before settling",
                        "ILLEGAL_TRANSITION", 409);
            }
            return attemptSettlement(journey, ticket, at, false);
        });
    }

    /**
     * Shared by the exit scan (first try) and settle (later tries).
     * Can pay: one debit, journey COMPLETED, ticket USED. Cannot pay: nothing moves, journey PAYMENT_PENDING.
     */
    private ScanResult attemptSettlement(Journey journey, QRTicket ticket, Instant at, boolean overtravelled) {
        long fareDue = journey.getFareDue();
        Account account = accountService.requireForPassenger(ticket.getPassengerId());
        Optional<FareTransaction> alreadyDebited = accountService.findFareTransaction(journey.getJourneyId());

        if (alreadyDebited.isPresent() || account.canPay(fareDue, at)) {                  // X5, A4
            FareTransaction debit = accountService.debitFare(account.getAccountId(), journey.getJourneyId(), fareDue, at);
            journey.complete(debit.getAmount());
            journeys.save(journey);
            ticket.markUsed();
            tickets.save(ticket);
            return ScanResult.of(ScanOutcome.JOURNEY_COMPLETED)
                    .journey(journey)
                    .ticket(ticket)
                    .fareDue(fareDue)
                    .fareCharged(debit.getAmount())
                    .balance(debit.getBalanceAfter())
                    .overtravelled(overtravelled)
                    .build();
        }

        if (journey.isOpen()) {                                                           // X6
            journey.markPaymentPending();
            journeys.save(journey);
        }
        ScanResult.Builder waiting = ScanResult.of(ScanOutcome.PAYMENT_PENDING)
                .journey(journey)
                .ticket(ticket)
                .fareDue(fareDue)
                .balance(account.getBalance())
                .overtravelled(overtravelled);
        if (!account.isUsableAt(at)) {
            return waiting.reason(RejectionReason.ACCOUNT_EXPIRED).build();
        }
        return waiting.shortfall(fareDue - account.getBalance()).build();
    }

    private ScanResult alreadySettled(Journey journey, QRTicket ticket) {
        return ScanResult.of(ScanOutcome.ALREADY_SETTLED)
                .journey(journey)
                .ticket(ticket)
                .fareDue(journey.getFareDue())
                .fareCharged(journey.getFareCharged())
                .build();
    }

    private Route requireRoute(String routeNo) {
        return routes.findRoute(routeNo).orElseThrow(() -> new NotFoundException("Route " + routeNo));
    }
}
