package com.example.ordermanagement.domain.exception;

/**
 * A write was rejected by a database constraint that wasn't the simple
 * duplicate-key case a {@code ResourceAlreadyExistsException} covers — a
 * violated foreign-key or check constraint, for instance. Translated by
 * {@code AbstractPersistenceAdapter} from Spring's
 * {@code DataIntegrityViolationException} when it can't be classified as a
 * duplicate. See {@code Validation.md}.
 */
public class PersistenceDataValidationException extends DomainException {

    public PersistenceDataValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}
