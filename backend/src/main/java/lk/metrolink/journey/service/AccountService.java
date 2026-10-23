package lk.metrolink.journey.service;

import java.time.Instant;
import java.util.Optional;
import lk.metrolink.journey.domain.Account;
import lk.metrolink.journey.domain.FareTransaction;
import lk.metrolink.journey.domain.NotFoundException;
import lk.metrolink.journey.log.Log;
import lk.metrolink.journey.repository.AccountRepository;
import lk.metrolink.journey.repository.FareTransactionRepository;

/**
 * Takes fares out of accounts.
 *
 * <p>Rule D2: one journey, one debit. Every debit carries the reference {@code FARE-<journeyId>}, which the store
 * keeps unique. Asking again for the same journey returns the first debit and touches nothing, so a repeated scan
 * or a retried request can never charge twice.
 */
public final class AccountService {
    private final AccountRepository accounts;
    private final FareTransactionRepository transactions;

    public AccountService(AccountRepository accounts, FareTransactionRepository transactions) {
        this.accounts = accounts;
        this.transactions = transactions;
    }

    public Account requireForPassenger(long passengerId) {
        return accounts.findByPassenger(passengerId)
                .orElseThrow(() -> new NotFoundException("Account for passenger " + passengerId));
    }

    public Optional<FareTransaction> findFareTransaction(long journeyId) {
        return transactions.findByReference(FareTransaction.referenceFor(journeyId));
    }

    /** Must run inside a unit of work. Throws if the account cannot pay; the caller checks {@code canPay} first. */
    public FareTransaction debitFare(long accountId, long journeyId, long amount, Instant at) {
        Optional<FareTransaction> earlier = findFareTransaction(journeyId);
        if (earlier.isPresent()) {
            Log.info(Log.ACCOUNT, "AccountService.debitFare: %s was already charged (rule D2), so the first debit is returned"
                    + " and nothing more is taken", earlier.get().getReference());
            return earlier.get();
        }
        Account account = accounts.findById(accountId)
                .orElseThrow(() -> new NotFoundException("Account " + accountId));
        long balanceBefore = account.getBalance();
        long balanceAfter = account.deduct(amount, at);
        accounts.save(account);
        Log.info(Log.ACCOUNT, "AccountService.debitFare: %s took LKR %d from account #%d, balance LKR %d -> LKR %d",
                FareTransaction.referenceFor(journeyId), amount, accountId, balanceBefore, balanceAfter);
        return transactions.insert(FareTransaction.forJourney(journeyId, accountId, amount, balanceAfter, at));
    }
}
