package lk.metrolink.journey.domain;

import java.util.List;

/** A registration with one or more wrong fields (rule O1). Carries every problem, not just the first. */
public class InvalidRegistrationException extends DomainException {
    private static final long serialVersionUID = 1L;

    private final transient List<OnboardingRequest.Problem> problems;

    public InvalidRegistrationException(List<OnboardingRequest.Problem> problems) {
        super("Invalid request", "VALIDATION", 400);
        this.problems = List.copyOf(problems);
    }

    public List<OnboardingRequest.Problem> problems() {
        return problems;
    }
}
