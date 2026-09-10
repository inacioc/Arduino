package com.example.ordermanagement.domain.exception;

/**
 * Two requests tried to modify the same resource concurrently and lost the
 * race — Spring's {@code ObjectOptimisticLockingFailureException}/
 * {@code ConcurrencyFailureException}, translated by
 * {@code AbstractPersistenceAdapter}. None of this application's entities carry
 * a {@code @Version} column today (see {@code Validation.md}'s note on that),
 * so this can't yet be triggered in practice — it's here so
 * {@code executeAndTranslate} has a correct, specific translation ready for
 * when one is added, instead of that case silently falling through to the
 * generic {@link InfrastructureUnavailableException} bucket.
 */
public class OptimisticLockingConflictException extends DomainException {

    private final String resourceName;
    private final String identifier;

    public OptimisticLockingConflictException(String resourceName, String identifier) {
        super(String.format("%s with identifier '%s' was modified concurrently by another request.",
                resourceName, identifier));
        this.resourceName = resourceName;
        this.identifier = identifier;
    }

    public String getResourceName() {
        return resourceName;
    }

    public String getIdentifier() {
        return identifier;
    }
}
