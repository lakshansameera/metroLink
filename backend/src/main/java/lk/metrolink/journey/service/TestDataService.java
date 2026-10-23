package lk.metrolink.journey.service;

import java.time.Instant;
import lk.metrolink.journey.domain.Account;
import lk.metrolink.journey.domain.Dates;
import lk.metrolink.journey.log.Log;
import lk.metrolink.journey.repository.AccountRepository;
import lk.metrolink.journey.repository.UnitOfWork;
import lk.metrolink.journey.service.view.PassengerView;

/**
 * Helpers for trying the reader out. Topping up credit is a different function (Top Up Credit), so this only
 * gives a demo passenger some money. It is not part of Journey Execution.
 */
public final class TestDataService {
    private final UnitOfWork uow;
    private final AccountService accountService;
    private final AccountRepository accounts;
    private final QueryService queries;
    private final Runnable resetAction;

    public TestDataService(UnitOfWork uow, AccountService accountService, AccountRepository accounts,
                           QueryService queries, Runnable resetAction) {
        this.uow = uow;
        this.accountService = accountService;
        this.accounts = accounts;
        this.queries = queries;
        this.resetAction = resetAction;
    }

    /** Adds credit to the passenger's account. The account itself refuses a bad amount or an expired account. */
    public PassengerView credit(long passengerId, long amount) {
        return credit(passengerId, amount, Dates.now());
    }

    public PassengerView credit(long passengerId, long amount, Instant at) {
        return uow.run(() -> {
            Account account = accountService.requireForPassenger(passengerId);
            long balanceBefore = account.getBalance();
            account.credit(amount, at);
            accounts.save(account);
            Log.info(Log.ACCOUNT, "TestDataService.credit: LKR %d added to account #%d, balance LKR %d -> LKR %d"
                    + " (stands in for Top Up Credit)", amount, account.getAccountId(), balanceBefore,
                    account.getBalance());
            return queries.passenger(passengerId);
        });
    }

    /** Throws away everything and loads the demo data again. */
    public void reset() {
        resetAction.run();
    }
}
