package com.example.ordermanagement.infrastructure.adapter.out.persistence;

import com.example.ordermanagement.domain.exception.DomainException;
import com.example.ordermanagement.domain.exception.InfrastructureUnavailableException;
import com.example.ordermanagement.domain.exception.OptimisticLockingConflictException;
import com.example.ordermanagement.domain.exception.PersistenceDataValidationException;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.TransientDataAccessException;

import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Supplier;

/**
 * Base class for every {@code *PersistenceAdapter}, translating Spring's
 * {@code DataAccessException} family into this application's {@link DomainException}
 * vocabulary in ONE place.
 * <p>
 * Why a shared base instead of a try/catch per adapter method: this application already
 * has two persistence adapters ({@code OrderPersistenceAdapter}, {@code ProductPersistenceAdapter})
 * across two tables, each with several methods that can fail the same handful of ways
 * (duplicate key, constraint violation, connection pool exhausted, query timeout...).
 * Repeating the same try/catch in every method is exactly the kind of "several tables,
 * same kind of error" duplication that invites drift. Wrapping each adapter method's body
 * in {@link #executeAndTranslate} gets every current and future persistence adapter the
 * same translation for free.
 * <p>
 * See {@code Validation.md} for the full rationale and the mapping this feeds into at the
 * web edge ({@code GlobalExceptionHandler}).
 */
public abstract class AbstractPersistenceAdapter {

    /**
     * Runs {@code action} and translates whatever Spring Data throws into a
     * {@link DomainException}.
     *
     * @param action           the JPA repository call to run.
     * @param entityName       human-readable entity name for the resulting exception's
     *                         message (e.g. {@code "Product"}).
     * @param identifier       the business identifier involved (e.g. a product name), for
     *                         the resulting exception's message.
     * @param duplicateSupplier builds the entity-specific exception to throw when the
     *                         failure is classified as a duplicate-key violation — e.g.
     *                         {@code (name, id) -> new ProductAlreadyExistsException(id)}.
     *                         Not invoked for any other failure.
     */
    protected <T> T executeAndTranslate(
            Supplier<T> action,
            String entityName,
            String identifier,
            BiFunction<String, String, DomainException> duplicateSupplier) {

        try {
            return action.get();
        } catch (DomainException e) {
            throw e; // Pass through - e.g. a *NotFoundException thrown by the action itself.
        } catch (DataIntegrityViolationException e) {
            if (isDuplicateKeyViolation(e)) {
                throw duplicateSupplier.apply(entityName, identifier);
            }
            throw new PersistenceDataValidationException(
                    "Constraint violation for %s [%s]".formatted(entityName, identifier), e);
        } catch (ConcurrencyFailureException e) {
            // Covers ObjectOptimisticLockingFailureException too (it's a subtype) -
            // a lost-update race on an entity with a @Version column, or any other
            // Spring-classified concurrent-modification conflict.
            throw new OptimisticLockingConflictException(entityName, identifier);
        } catch (TransientDataAccessException | DataAccessResourceFailureException e) {
            throw new InfrastructureUnavailableException(
                    "Database infrastructure is temporarily unavailable", e);
        } catch (DataAccessException e) {
            throw new InfrastructureUnavailableException(
                    "Persistence error for %s [%s]".formatted(entityName, identifier), e);
        }
    }

    /**
     * Convenience for calls that can never legitimately produce a duplicate-key
     * violation (reads, deletes) — no entity-specific exception to build, so any
     * {@code DataIntegrityViolationException} that somehow occurs anyway is reported
     * as a generic constraint violation rather than requiring every read-only call
     * site to invent a throwaway duplicate handler it will never see used.
     */
    protected <T> T executeAndTranslate(Supplier<T> action, String entityName, String identifier) {
        return executeAndTranslate(action, entityName, identifier, (name, id) -> {
            throw new PersistenceDataValidationException(
                    "Unexpected duplicate-key signal for a non-create operation on %s [%s]".formatted(name, id),
                    null);
        });
    }

    private boolean isDuplicateKeyViolation(DataIntegrityViolationException e) {
        String message = Optional.ofNullable(e.getRootCause())
                .map(Throwable::getMessage)
                .orElse("")
                .toLowerCase();
        return message.contains("duplicate") || message.contains("unique");
    }
}
