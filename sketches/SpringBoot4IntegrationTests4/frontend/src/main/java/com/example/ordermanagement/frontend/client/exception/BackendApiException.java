package com.example.ordermanagement.frontend.client.exception;

/**
 * Base type for a problem reported by adapter-in-web's REST API (translated from
 * its {@code ProblemDetail} response body). Frontend controllers catch the
 * specific subtype they know how to map onto a form/flash message; anything
 * that surfaces as the base type falls through to {@code MvcExceptionHandler}.
 */
public class BackendApiException extends RuntimeException {

    public BackendApiException(String message) {
        super(message);
    }
}
