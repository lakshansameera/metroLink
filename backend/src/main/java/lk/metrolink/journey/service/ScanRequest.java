package lk.metrolink.journey.service;

import java.time.Instant;

/** One tap as the reader reports it. {@code scannedAt} is optional: the server's clock is used when it is missing. */
public final class ScanRequest {
    private final String qrCode;
    private final ScanDirection direction;
    private final String stopId;
    private final Instant scannedAt;

    public ScanRequest(String qrCode, ScanDirection direction, String stopId, Instant scannedAt) {
        this.qrCode = qrCode;
        this.direction = direction;
        this.stopId = stopId;
        this.scannedAt = scannedAt;
    }

    public String getQrCode() { return qrCode; }
    public ScanDirection getDirection() { return direction; }
    public String getStopId() { return stopId; }
    public Instant getScannedAt() { return scannedAt; }
}
