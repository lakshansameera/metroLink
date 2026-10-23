package lk.metrolink.journey.service;

import java.time.Instant;

/** What the reader forwards with every scan: the token, the stop it is at, and when it happened. */
public final class Scan {
    private final String qrCode;
    private final String stopId;
    private final Instant scannedAt;

    public Scan(String qrCode, String stopId, Instant scannedAt) {
        this.qrCode = qrCode;
        this.stopId = stopId;
        this.scannedAt = scannedAt;
    }

    public String getQrCode() { return qrCode; }
    public String getStopId() { return stopId; }
    public Instant getScannedAt() { return scannedAt; }
}
