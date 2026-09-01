package com.example.sso.appa.client;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * Performs the three outbound calls this lab is about. Each method differs only in which
 * pre-configured {@link RestClient} it uses - and therefore in which identity is presented.
 *
 * <p>Errors are turned into a {@link CallOutcome} rather than rethrown: a 401 or 403 from a
 * downstream application is an interesting result to look at, not a crash.
 */
@Service
public class DownstreamCaller {

    private final RestClient appBMachineClient;
    private final RestClient appCMachineClient;
    private final RestClient appBUserClient;

    public DownstreamCaller(
            @Qualifier("appBMachineClient") RestClient appBMachineClient,
            @Qualifier("appCMachineClient") RestClient appCMachineClient,
            @Qualifier("appBUserClient") RestClient appBUserClient) {
        this.appBMachineClient = appBMachineClient;
        this.appCMachineClient = appCMachineClient;
        this.appBUserClient = appBUserClient;
    }

    /** Scheduled, same SSO domain, application identity. */
    public CallOutcome fetchAppBReportsAsMachine() {
        return call("Application B /api/reports as app-a-m2m (client_credentials, domain A)",
                appBMachineClient, "/api/reports");
    }

    /** Scheduled, across SSO domains, application identity issued by domain C. */
    public CallOutcome fetchAppCInventoryAsMachine() {
        return call("Application C /api/inventory as app-a-federated-m2m (client_credentials, domain C)",
                appCMachineClient, "/api/inventory");
    }

    /** User-triggered, same endpoint as {@link #fetchAppBReportsAsMachine()}, user's token. */
    public CallOutcome fetchAppBReportsAsCurrentUser() {
        return call("Application B /api/reports with the logged-in user's token (relay)",
                appBUserClient, "/api/reports");
    }

    private CallOutcome call(String label, RestClient client, String path) {
        try {
            ResourceResponse response = client.get()
                    .uri(path)
                    .retrieve()
                    .body(ResourceResponse.class);
            if (response == null) {
                return CallOutcome.failure(label, "empty response body");
            }
            return CallOutcome.success(label, response);
        }
        catch (RestClientResponseException ex) {
            // 401 = the token was missing, expired, from the wrong issuer, or for the wrong
            // audience. 403 = the token was valid but lacked the required role.
            return CallOutcome.failure(label, "HTTP %d %s - %s".formatted(
                    ex.getStatusCode().value(),
                    ex.getStatusText(),
                    firstLine(ex.getResponseBodyAsString())));
        }
        catch (Exception ex) {
            // Typically the downstream application is not running, or the token endpoint of the
            // relevant Keycloak instance is unreachable.
            return CallOutcome.failure(label, "%s: %s".formatted(
                    ex.getClass().getSimpleName(), ex.getMessage()));
        }
    }

    private static String firstLine(String body) {
        if (body == null || body.isBlank()) {
            return "(no body)";
        }
        String trimmed = body.strip();
        int newline = trimmed.indexOf('\n');
        String line = (newline < 0) ? trimmed : trimmed.substring(0, newline);
        return line.length() > 300 ? line.substring(0, 300) + "..." : line;
    }
}
