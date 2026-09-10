package com.example.ordermanagement.infrastructure.adapter.in.web.error;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Produces a {@code ResponseApiError} body for 401s (missing/invalid/expired bearer
 * token) instead of Spring Security's default empty response.
 * <p>
 * This fires from inside the security filter chain — for a missing/invalid JWT, the
 * OAuth2 Resource Server's filters reject the request before it ever reaches
 * {@code DispatcherServlet}, so {@code GlobalExceptionHandler}'s
 * {@code @ExceptionHandler} methods are never in the running for this case no matter
 * what they're annotated with. Registering this bean as {@code .oauth2ResourceServer()
 * .authenticationEntryPoint(...)} in {@code SecurityConfig} is the correct (and only)
 * way to make 401 bodies consistent with the rest of the API. See {@code Validation.md}.
 */
@Component
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ErrorResponseWriter responseWriter;
    private final ErrorDocs errorDocs;

    public RestAuthenticationEntryPoint(ErrorResponseWriter responseWriter, ErrorDocs errorDocs) {
        this.responseWriter = responseWriter;
        this.errorDocs = errorDocs;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                          AuthenticationException authException) throws IOException {
        responseWriter.write(response, HttpStatus.UNAUTHORIZED, ApiError.builder()
                .code("UNAUTHENTICATED")
                .level(ErrorLevel.BLOCKING)
                .label("Authentication Required")
                .description("A valid bearer token is required to access this resource.")
                .uriDesc(errorDocs.uriFor("UNAUTHENTICATED"))
                .build());
    }
}
