package com.example.ordermanagement.frontend.client.exception;

/**
 * adapter-in-web returned 409 - its {@code Order} aggregate rejected an illegal
 * status transition ({@code IllegalStateException}), mapped to ProblemDetail.
 */
public class BackendConflictException extends BackendApiException {

    public BackendConflictException(String message) {
        super(message);
    }
}
