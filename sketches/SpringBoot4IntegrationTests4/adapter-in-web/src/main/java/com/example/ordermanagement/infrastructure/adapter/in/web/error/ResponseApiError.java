package com.example.ordermanagement.infrastructure.adapter.in.web.error;

import lombok.Builder;
import lombok.Data;
import org.springframework.http.HttpStatus;

import java.util.List;

/**
 * The single response body shape {@code GlobalExceptionHandler} uses for every
 * error this API can return — Bean Validation failures, domain/functional
 * errors (not found, already exists, business-rule/notification validation),
 * and infrastructure failures (database or broker unavailable) alike. One
 * envelope, one contract for every client to code against, instead of a
 * different ad hoc shape per exception type.
 * <p>
 * {@link #status} duplicates the HTTP response's own status code inside the
 * body. That's intentional, not an oversight: it keeps the payload
 * self-describing for a consumer that logs/stores the body separately from
 * the transport-level response (a message queue relay, an audit log, a test
 * assertion against a recorded body) without needing the HTTP envelope
 * alongside it.
 */
@Data
@Builder
public class ResponseApiError {

    private final HttpStatus status;
    private final List<ApiError> errors;
}
