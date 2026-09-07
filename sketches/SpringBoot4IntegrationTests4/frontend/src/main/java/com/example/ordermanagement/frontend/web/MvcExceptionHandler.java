package com.example.ordermanagement.frontend.web;

import com.example.ordermanagement.frontend.client.exception.BackendApiException;
import com.example.ordermanagement.frontend.client.exception.BackendNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Safety net for what {@link ProductMvcController}/{@link OrderMvcController}
 * don't catch locally to map onto a form field or flash message - a stale link
 * to a deleted order (404), or adapter-in-web being unreachable/returning
 * something unexpected (shown as a generic error page rather than a stack trace
 * or the Whitelabel error page).
 */
@ControllerAdvice
public class MvcExceptionHandler {

    @ExceptionHandler(BackendNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public String handleNotFound() {
        return "error/not-found";
    }

    @ExceptionHandler(BackendApiException.class)
    @ResponseStatus(HttpStatus.BAD_GATEWAY)
    public String handleBackendError() {
        return "error/general";
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public String handleUnexpected() {
        return "error/general";
    }
}
