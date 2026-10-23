package lk.metrolink.journey.service.view;

import java.time.LocalDate;

/** A passenger and their account, as the screens show them. {@code visaExpiry} is set for temporary accounts only. */
public final class PassengerView {
    private final long passengerId;
    private final String name;
    private final String email;
    private final String residency;
    private final long accountId;
    private final String accountType;
    private final long balance;
    private final LocalDate visaExpiry;

    public PassengerView(long passengerId, String name, String email, String residency, long accountId,
                         String accountType, long balance, LocalDate visaExpiry) {
        this.passengerId = passengerId;
        this.name = name;
        this.email = email;
        this.residency = residency;
        this.accountId = accountId;
        this.accountType = accountType;
        this.balance = balance;
        this.visaExpiry = visaExpiry;
    }

    public long getPassengerId() { return passengerId; }
    public String getName() { return name; }
    public String getEmail() { return email; }
    public String getResidency() { return residency; }
    public long getAccountId() { return accountId; }
    public String getAccountType() { return accountType; }
    public long getBalance() { return balance; }
    public LocalDate getVisaExpiry() { return visaExpiry; }
}
