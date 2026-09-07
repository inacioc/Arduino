package com.example.ordermanagement.frontend.client;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Where adapter-in-web lives and how to authenticate against it. There is no
 * Keycloak client/realm registered for this frontend yet, so {@code accessToken}
 * is a static bearer token you obtain externally (e.g. a Keycloak password-grant
 * call) and paste into config/env - see FRONTEND_VALIDATION.md. It is attached
 * as-is to every outgoing call; leave it blank only if adapter-in-web's security
 * has been disabled for local testing.
 */
@ConfigurationProperties(prefix = "app.backend")
public record BackendProperties(
        String baseUrl,
        String accessToken,
        Duration connectTimeout,
        Duration readTimeout
) {
    public BackendProperties {
        if (connectTimeout == null) {
            connectTimeout = Duration.ofSeconds(5);
        }
        if (readTimeout == null) {
            readTimeout = Duration.ofSeconds(10);
        }
    }
}
