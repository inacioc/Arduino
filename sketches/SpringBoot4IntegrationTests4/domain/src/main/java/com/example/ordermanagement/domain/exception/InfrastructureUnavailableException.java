package com.example.ordermanagement.domain.exception;

/**
 * The infrastructure behind a port could not be reached or failed for reasons
 * unrelated to the request's content — the database connection pool is
 * exhausted, a query timed out, the message broker is unreachable. Never the
 * caller's fault, and the request may well succeed if retried later.
 * <p>
 * Translated by driven adapters from whatever their client library threw:
 * {@code AbstractPersistenceAdapter} from Spring Data's
 * {@code DataAccessException}/{@code TransactionException} family, and
 * {@code OrderMqPublisher} from Spring's {@code JmsException} for IBM MQ. One
 * type for both rather than a separate "database unavailable" /
 * "broker unavailable" pair: from a driving adapter's point of view they mean
 * exactly the same thing — 503, log it, nothing the client did wrong. See
 * {@code Validation.md}.
 */
public class InfrastructureUnavailableException extends DomainException {

    public InfrastructureUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
