package lk.metrolink.journey.service;

/** What a scan, or a later settlement, led to. */
public enum ScanOutcome {
    JOURNEY_STARTED,
    JOURNEY_COMPLETED,
    PAYMENT_PENDING,
    ALREADY_SETTLED,
    REJECTED
}
