package lk.metrolink.journey.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import lk.metrolink.journey.domain.Alert;
import lk.metrolink.journey.domain.AlertAudience;
import lk.metrolink.journey.domain.AlertType;

public interface AlertRepository {
    /** Gives the alert an id and stores it. */
    Alert insert(Alert draft);

    Optional<Alert> findById(long alertId);

    /** Every alert, newest first. */
    List<Alert> list();

    /** The alerts this audience must see, newest first. */
    List<Alert> listFor(AlertAudience audience);

    /** True when the journey has an alert of this type that is not resolved yet. */
    boolean hasOpenAlert(long journeyId, AlertType type);

    /** Closes the journey's open alerts of this type (rule D1). Nothing happens if there are none. */
    void resolve(long journeyId, AlertType type, Instant resolvedAt);
}