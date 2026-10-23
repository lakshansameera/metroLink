package lk.metrolink.journey.domain;

/**
 * The two answers a customer can give about residency when registering. It is only a list of labels, so it is an
 * enum. The behaviour that follows from the answer (which account to open) lives in {@link Residency}.
 */
public enum ResidencyKind {
    RESIDENT,
    NON_RESIDENT
}
