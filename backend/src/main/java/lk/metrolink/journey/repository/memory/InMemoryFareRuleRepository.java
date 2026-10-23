package lk.metrolink.journey.repository.memory;

import java.util.Comparator;
import java.util.List;
import lk.metrolink.journey.domain.FareRule;
import lk.metrolink.journey.repository.FareRuleRepository;

public final class InMemoryFareRuleRepository implements FareRuleRepository {
    private final InMemoryDatabase db;

    public InMemoryFareRuleRepository(InMemoryDatabase db) {
        this.db = db;
    }

    @Override
    public List<FareRule> listForRoute(String routeNo) {
        return db.tables().fareRules.values().stream()
                .filter(r -> r.getRouteNo().equals(routeNo))
                .sorted(Comparator.comparing(FareRule::getEffectiveFrom))
                .toList();
    }
}
