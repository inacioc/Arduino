package com.example.ordermanagement.frontend.client.exception;

/** adapter-in-web returned 404 (its {@code OrderNotFoundException} mapped to ProblemDetail). */
public class BackendNotFoundException extends BackendApiException {

    public BackendNotFoundException(String message) {
        super(message);
    }
}
