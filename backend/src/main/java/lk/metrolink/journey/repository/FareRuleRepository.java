package lk.metrolink.journey.repository;

import java.util.List;
import lk.metrolink.journey.domain.FareRule;

public interface FareRuleRepository {
        List<FareRule> listForRoute(String routeNo);
}