package lk.metrolink.journey.service;

import java.time.Instant;
import lk.metrolink.journey.domain.Dates;
import lk.metrolink.journey.domain.NotFoundException;
import lk.metrolink.journey.repository.RouteRepository;
import lk.metrolink.journey.repository.UnitOfWork;
import lk.metrolink.journey.service.ReaderDisplay.FareFigures;
import lk.metrolink.journey.service.ReaderDisplay.Tone;

/**
 * The on-board reader, as one simple front door (the Facade pattern): give it a tap, get back what to show.
 *
 * <p>It does no business work. It hands the tap to {@link JourneyService}, then turns the structured answer into a
 * tone, a sentence and the figures behind it. The whole tap, answer included, is one unit of work.
 */
public final class ReaderService {
    private final UnitOfWork uow;
    private final JourneyService journeyService;
    private final RouteRepository routes;
    private final QueryService queries;

    public ReaderService(UnitOfWork uow, JourneyService journeyService, RouteRepository routes, QueryService queries) {
        this.uow = uow;
        this.journeyService = journeyService;
        this.routes = routes;
        this.queries = queries;
    }

    public ReaderDisplay scan(ScanRequest request) {
        return uow.run(() -> {
            // A stop the network has never heard of is a wrong request, not a rejected passenger.
            routes.findStop(request.getStopId())
                    .orElseThrow(() -> new NotFoundException("Stop " + request.getStopId()));
            Instant at = request.getScannedAt() != null ? request.getScannedAt() : Dates.now();
            Scan scan = new Scan(request.getQrCode(), request.getStopId(), at);
            ScanResult result = request.getDirection() == ScanDirection.IN
                    ? journeyService.board(scan)
                    : journeyService.alight(scan);
            return display(result);
        });
    }

    /** The passenger has topped up and asks for the waiting fare to be paid. */
    public ReaderDisplay settle(long journeyId) {
        return settle(journeyId, Dates.now());
    }

    public ReaderDisplay settle(long journeyId, Instant at) {
        return uow.run(() -> display(journeyService.settle(journeyId, at)));
    }

    private ReaderDisplay display(ScanResult r) {
        Tone tone = toneOf(r);
        return new ReaderDisplay(
                r.getOutcome(),
                tone,
                messageOf(r),
                r.getReason(),
                r.getTicket() == null ? null : queries.ticketView(r.getTicket()),
                r.getJourney() == null ? null : queries.journeyView(r.getJourney()),
                new FareFigures(r.getFareDue(), r.getFareCharged(), r.getBalance(), r.getShortfall(), r.getRequired()),
                r.isOvertravelled());
    }

    private static Tone toneOf(ScanResult r) {
        switch (r.getOutcome()) {
            case JOURNEY_STARTED:
            case JOURNEY_COMPLETED:
                return Tone.SUCCESS;
            case PAYMENT_PENDING:
            case ALREADY_SETTLED:
                return Tone.WARNING;
            default:
                return Tone.ERROR;
        }
    }

    private static String messageOf(ScanResult r) {
        switch (r.getOutcome()) {
            case JOURNEY_STARTED:
                return "Journey started. Your fare will be charged when you get off.";
            case JOURNEY_COMPLETED:
                String paid = "Fare LKR " + r.getFareCharged() + " charged. Balance LKR " + r.getBalance() + ".";
                return r.isOvertravelled() ? paid + " You travelled past your planned stop." : paid;
            case PAYMENT_PENDING:
                if (r.getReason() == RejectionReason.ACCOUNT_EXPIRED) {
                    return "Fare LKR " + r.getFareDue() + " is owed, but this account has expired and cannot be charged.";
                }
                return "Fare LKR " + r.getFareDue() + " is owed. Top up at least LKR " + r.getShortfall()
                        + " to complete this journey.";
            case ALREADY_SETTLED:
                return "This journey is already complete (LKR " + r.getFareCharged() + "). Nothing more was charged.";
            default:
                return rejectionMessage(r);
        }
    }

    private static String rejectionMessage(ScanResult r) {
        switch (r.getReason()) {
            case UNKNOWN_TOKEN:
                return "This QR code is not recognised.";
            case TICKET_EXPIRED:
                return "This ticket has expired.";
            case WRONG_TRAVEL_DATE:
                return "This ticket is for a different day.";
            case TICKET_CANCELLED:
                return "This ticket was cancelled.";
            case TICKET_ALREADY_USED:
                return "This ticket has already been used.";
            case ALREADY_BOARDED:
                return "This ticket already has a journey in progress.";
            case INVALID_BOARDING_STOP:
                return "You cannot board here with this ticket.";
            case INSUFFICIENT_CREDIT:
                return "Not enough credit to board: this trip needs LKR " + r.getRequired() + " and your balance is LKR "
                        + r.getBalance() + ". Please top up.";
            case ACCOUNT_EXPIRED:
                return "This account has expired, so the journey cannot start.";
            case MISSING_BOARDING_SCAN:
                return "There is no boarding scan for this ticket.";
            case INVALID_EXIT_STOP:
                return "This is not a valid stop to get off at on your journey. Nothing was charged.";
            default:
                return "The scan was not accepted.";
        }
    }
}
