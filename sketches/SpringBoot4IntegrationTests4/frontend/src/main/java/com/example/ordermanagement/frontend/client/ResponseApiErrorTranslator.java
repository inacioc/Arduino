package com.example.ordermanagement.frontend.client;

import com.example.ordermanagement.frontend.client.dto.ApiErrorDto;
import com.example.ordermanagement.frontend.client.dto.ResponseApiErrorDto;
import com.example.ordermanagement.frontend.client.exception.BackendConflictException;
import com.example.ordermanagement.frontend.client.exception.BackendNotFoundException;
import com.example.ordermanagement.frontend.client.exception.BackendOrderValidationException;
import com.example.ordermanagement.frontend.client.exception.BackendUnavailableException;
import com.example.ordermanagement.frontend.client.exception.OrderItemErrorDto;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.UUID;

/**
 * Turns adapter-in-web's {@code ResponseApiError} error responses back into typed
 * exceptions the frontend controllers can catch - the HTTP equivalent of catching the
 * domain exceptions directly, which is what the REST API's own
 * {@code GlobalExceptionHandler} does one hop upstream.
 */
final class ResponseApiErrorTranslator {

    private ResponseApiErrorTranslator() {
    }

    static RuntimeException translate(RestClientResponseException ex, ObjectMapper objectMapper) {
        ResponseApiErrorDto body = safeRead(ex, objectMapper);
        String detail = summarize(body, ex.getMessage());
        HttpStatusCode status = ex.getStatusCode();

        if (status.equals(HttpStatus.NOT_FOUND)) {
            return new BackendNotFoundException(detail);
        }
        if (status.equals(HttpStatus.CONFLICT)) {
            return new BackendConflictException(detail);
        }
        if (status.equals(HttpStatus.UNPROCESSABLE_CONTENT) && body != null) {
            return new BackendOrderValidationException(detail, itemErrors(body));
        }
        return new BackendUnavailableException(
                "adapter-in-web returned " + status + ": " + detail, ex);
    }

    private static ResponseApiErrorDto safeRead(RestClientResponseException ex, ObjectMapper objectMapper) {
        byte[] responseBody = ex.getResponseBodyAsByteArray();
        if (responseBody == null || responseBody.length == 0) {
            return null;
        }
        try {
            return objectMapper.readValue(responseBody, ResponseApiErrorDto.class);
        } catch (RuntimeException parseFailure) {
            return null;
        }
    }

    /** Every {@code ApiError} description, joined - good enough for a flash message/generic form error. */
    private static String summarize(ResponseApiErrorDto body, String fallback) {
        if (body == null || body.errors() == null || body.errors().isEmpty()) {
            return fallback;
        }
        return body.errors().stream()
                .map(ApiErrorDto::description)
                .filter(d -> d != null && !d.isBlank())
                .reduce((a, b) -> a + "; " + b)
                .orElse(fallback);
    }

    /**
     * Flattens every {@code ApiError.errorsValueList} entry back into one
     * {@link OrderItemErrorDto} per affected order line - the inverse of how
     * {@code GlobalExceptionHandler} groups {@code OrderValidationException}'s
     * per-line errors into one {@code ApiError} per code.
     */
    private static List<OrderItemErrorDto> itemErrors(ResponseApiErrorDto body) {
        return body.errors().stream()
                .flatMap(apiError -> apiError.errorsValueList().stream()
                        .map(value -> toItemError(apiError, value)))
                .toList();
    }

    private static OrderItemErrorDto toItemError(ApiErrorDto apiError, String value) {
        try {
            return new OrderItemErrorDto(UUID.fromString(value), apiError.code(), apiError.description());
        } catch (IllegalArgumentException notAUuid) {
            // Shouldn't happen for order-validation errors (the value is always a
            // productId), but don't let a future non-UUID errorsValueList entry blow up
            // the whole response translation.
            return null;
        }
    }
}
