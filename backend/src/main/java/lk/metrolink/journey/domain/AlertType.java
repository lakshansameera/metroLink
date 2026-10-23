package lk.metrolink.journey.domain;

/** What an alert is about. A plain list of labels. */
public enum AlertType {
    INVALID_TICKET,         // a ticket was refused at boarding
    INSUFFICIENT_CREDIT,    // the passenger cannot afford the trip at boarding
    MISSING_BOARDING_SCAN,  // someone scanned out without ever scanning
    PAYMENT_PENDING,        // the fare could not be paid at exit
    OVERTRAVEL              // the passenger got off past the planned stop
}