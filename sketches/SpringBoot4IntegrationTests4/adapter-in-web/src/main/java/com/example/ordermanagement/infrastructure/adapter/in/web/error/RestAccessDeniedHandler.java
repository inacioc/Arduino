package com.example.ordermanagement.infrastructure.adapter.in.web.error;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Produces a {@code ResponseApiError} body for 403s raised at the filter-chain level
 * ({@code authorizeHttpRequests} rules) — the counterpart to {@link RestAuthenticationEntryPoint}.
 * <p>
 * {@code @PreAuthorize} denials are different: they're thrown by a method-security AOP
 * interceptor around the controller bean, i.e. <em>inside</em> {@code DispatcherServlet}'s
 * handler invocation, so they reach {@code GlobalExceptionHandler}'s
 * {@code @ExceptionHandler(AccessDeniedException.class)} instead and never get here — Spring
 * MVC resolves the exception via its {@code HandlerExceptionResolver} chain before it would
 * ever propagate out to this filter-level handler. Both are registered so a denial produces
 * the same body regardless of which layer caught it. See {@code Validation.md}.
 */
@Component
public class RestAccessDeniedHandler implements AccessDeniedHandler {

    private final ErrorResponseWriter responseWriter;
    private final ErrorDocs errorDocs;

    public RestAccessDeniedHandler(ErrorResponseWriter responseWriter, ErrorDocs errorDocs) {
        this.responseWriter = responseWriter;
        this.errorDocs = errorDocs;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                        AccessDeniedException accessDeniedException) throws IOException {
        responseWriter.write(response, HttpStatus.FORBIDDEN, ApiError.builder()
                .code("ACCESS_DENIED")
                .level(ErrorLevel.BLOCKING)
                .label("Access Denied")
                .description("You do not have permission to perform this action.")
                .uriDesc(errorDocs.uriFor("ACCESS_DENIED"))
                .build());
    }
}
