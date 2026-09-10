package com.example.ordermanagement.infrastructure.adapter.in.web.error;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Builds the {@link ApiError#getUriDesc()} link for an error code — a
 * dereferenceable URI a client (or a developer reading a bug report) can follow
 * to this error code's documentation, the way RFC 7807's {@code type} is meant
 * to be used. Centralized so the base URI is configured once
 * ({@code app.errors.docs-base-uri}) rather than hardcoded at every call site.
 */
@Component
public class ErrorDocs {

    private final String baseUri;

    public ErrorDocs(@Value("${app.errors.docs-base-uri:https://errors.ordermanagement.example.com/}") String baseUri) {
        this.baseUri = baseUri.endsWith("/") ? baseUri : baseUri + "/";
    }

    public String uriFor(String code) {
        return baseUri + code;
    }
}
