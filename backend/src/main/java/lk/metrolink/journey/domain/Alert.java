package lk.metrolink.journey.domain;

import java.time.Instant;
import java.util.List;

/**
 * A notice for the driver or an inspector, raised as a consequence of something that happened (a
 * refused ticket, a payment shortfall, overtravel). An alert never decides how a journey ends.
 * An alert never changes: every "change" returns a new Alert. It is open until it is resolved.
 */
public final class Alert {
    private final long alertId;
    private final AlertType type;
    private final AlertStage stage;
    private final String message;
    private final List<AlertAudience> audience;
    private final Instant createdAt;
    private final InvalidTicketReason reason;
    private final Long journeyId;
    private final Long ticketId;
    private final String qrCode;
    private final Instant resolvedAt;

    private Alert(long alertId, AlertType type, AlertStage stage, String message, List<AlertAudience> audience,
                  Instant createdAt, InvalidTicketReason reason, Long journeyId, Long ticketId, String qrCode,
                  Instant resolvedAt) {
        this.alertId = alertId;
        this.type = type;
        this.stage = stage;
        this.message = message;
        this.audience = List.copyOf(audience);
        this.createdAt = createdAt;
        this.reason = reason;
        this.journeyId = journeyId;
        this.ticketId = ticketId;
        this.qrCode = qrCode;
        this.resolvedAt = resolvedAt;
    }

    public static Alert raise(AlertType type, AlertStage stage, String message, Instant createdAt,
                              AlertAudience... audience) {
        return new Alert(0, type, stage, message, List.of(audience), createdAt, null, null, null, null, null);
    }

    public Alert forJourney(long journeyId) {
        return new Alert(alertId, type, stage, message, audience, createdAt, reason, journeyId, ticketId, qrCode,
                resolvedAt);
    }

    public Alert forTicket(long ticketId) {
        return new Alert(alertId, type, stage, message, audience, createdAt, reason, journeyId, ticketId, qrCode,
                resolvedAt);
    }

    public Alert forQrCode(String qrCode) {
        return new Alert(alertId, type, stage, message, audience, createdAt, reason, journeyId, ticketId, qrCode,
                resolvedAt);
    }

    public Alert because(InvalidTicketReason reason) {
        return new Alert(alertId, type, stage, message, audience, createdAt, reason, journeyId, ticketId, qrCode,
                resolvedAt);
    }

    public Alert withId(long newAlertId) {
        return new Alert(newAlertId, type, stage, message, audience, createdAt, reason, journeyId, ticketId, qrCode,
                resolvedAt);
    }

     public Alert resolve(Instant at) {
        if (resolvedAt != null) return this;
        return new Alert(alertId, type, stage, message, audience, createdAt, reason, journeyId, ticketId, qrCode, at);
    }

    public boolean isOpen() {
        return resolvedAt == null;
    }

    public boolean isFor(AlertAudience who) {
        return audience.contains(who);
    }

    public long getAlertId() { return alertId; }
    public AlertType getType() { return type; }
    public AlertStage getStage() { return stage; }
    public String getMessage() { return message; }
    public List<AlertAudience> getAudience() { return audience; }
    public Instant getCreatedAt() { return createdAt; }
    public InvalidTicketReason getReason() { return reason; }
    public Long getJourneyId() { return journeyId; }
    public Long getTicketId() { return ticketId; }
    public String getQrCode() { return qrCode; }
    public Instant getResolvedAt() { return resolvedAt; }
}