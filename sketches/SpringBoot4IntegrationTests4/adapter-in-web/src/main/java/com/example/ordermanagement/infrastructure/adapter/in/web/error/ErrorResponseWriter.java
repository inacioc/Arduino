package com.example.ordermanagement.infrastructure.adapter.in.web.error;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.List;

/**
 * Writes a {@link ResponseApiError} straight to a servlet response — for the two
 * places that can't go through {@code GlobalExceptionHandler} because the failure
 * happens in the Spring Security filter chain, before {@code DispatcherServlet}
 * (and therefore before any {@code @ExceptionHandler}) ever runs: see
 * {@link RestAuthenticationEntryPoint} and {@link RestAccessDeniedHandler}.
 * Kept as one shared helper so both produce byte-for-byte the same envelope
 * {@code GlobalExceptionHandler} does, rather than each hand-rolling JSON.
 */
@Component
class ErrorResponseWriter {

    private final ObjectMapper objectMapper;

    ErrorResponseWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    void write(HttpServletResponse response, HttpStatus status, ApiError error) throws IOException {
        ResponseApiError body = ResponseApiError.builder()
                .status(status)
                .errors(List.of(error))
                .build();

        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(), body);
    }
}
