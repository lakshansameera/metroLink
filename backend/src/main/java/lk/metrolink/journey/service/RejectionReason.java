package lk.metrolink.journey.service;

import lk.metrolink.journey.domain.InvalidTicketReason;

/**
 * Why a scan was refused, or why a fare is waiting for payment: any invalid-ticket reason, or one of the
 * journey-level reasons.
 */
public enum RejectionReason {
    UNKNOWN_TOKEN,
    TICKET_EXPIRED,
    WRONG_TRAVEL_DATE,
    TICKET_CANCELLED,
    TICKET_ALREADY_USED,
    ALREADY_BOARDED,
    INVALID_BOARDING_STOP,
    INSUFFICIENT_CREDIT,
    MISSING_BOARDING_SCAN,
    INVALID_EXIT_STOP,
    ACCOUNT_EXPIRED;

    public static RejectionReason from(InvalidTicketReason reason) {
        return valueOf(reason.name());
    }
}
