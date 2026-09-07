package com.example.ordermanagement.frontend.client.exception;

/**
 * adapter-in-web could not be reached at all (connection refused/timeout), or
 * responded with an unmapped 4xx/5xx. Always a safety-net case - a healthy
 * backend should never produce this for a well-formed request.
 */
public class BackendUnavailableException extends BackendApiException {

    public BackendUnavailableException(String message, Throwable cause) {
        super(message);
        initCause(cause);
    }

    public BackendUnavailableException(String message) {
        super(message);
    }
}
