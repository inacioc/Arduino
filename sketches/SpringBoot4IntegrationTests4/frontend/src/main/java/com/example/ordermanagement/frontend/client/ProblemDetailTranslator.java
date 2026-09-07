package com.example.ordermanagement.frontend.client;

import com.example.ordermanagement.frontend.client.exception.BackendConflictException;
import com.example.ordermanagement.frontend.client.exception.BackendNotFoundException;
import com.example.ordermanagement.frontend.client.exception.BackendOrderValidationException;
import com.example.ordermanagement.frontend.client.exception.BackendUnavailableException;
import com.example.ordermanagement.frontend.client.exception.OrderItemErrorDto;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

/**
 * Turns adapter-in-web's {@code ProblemDetail} error responses (RFC 7807) back
 * into typed exceptions the frontend controllers can catch - the HTTP
 * equivalent of catching the domain exceptions directly, which is what the
 * REST API's own {@code GlobalExceptionHandler} does one hop upstream.
 */
final class ProblemDetailTranslator {

    private ProblemDetailTranslator() {
    }

    static RuntimeException translate(RestClientResponseException ex, ObjectMapper objectMapper) {
        ProblemDetail problem = safeReadProblemDetail(ex, objectMapper);
        String detail = (problem != null && problem.getDetail() != null) ? problem.getDetail() : ex.getMessage();
        HttpStatusCode status = ex.getStatusCode();

        if (status.equals(HttpStatus.NOT_FOUND)) {
            return new BackendNotFoundException(detail);
        }
        if (status.equals(HttpStatus.CONFLICT)) {
            return new BackendConflictException(detail);
        }
        if (status.equals(HttpStatus.UNPROCESSABLE_ENTITY) && problem != null) {
            return new BackendOrderValidationException(detail, itemErrors(problem, objectMapper));
        }
        return new BackendUnavailableException(
                "adapter-in-web returned " + status + ": " + detail, ex);
    }

    private static ProblemDetail safeReadProblemDetail(RestClientResponseException ex, ObjectMapper objectMapper) {
        byte[] body = ex.getResponseBodyAsByteArray();
        if (body == null || body.length == 0) {
            return null;
        }
        try {
            return objectMapper.readValue(body, ProblemDetail.class);
        } catch (RuntimeException parseFailure) {
            return null;
        }
    }

    private static List<OrderItemErrorDto> itemErrors(ProblemDetail problem, ObjectMapper objectMapper) {
        Object raw = problem.getProperties() == null ? null : problem.getProperties().get("errors");
        if (raw == null) {
            return List.of();
        }
        return objectMapper.convertValue(raw, new TypeReference<List<OrderItemErrorDto>>() {});
    }
}
