package com.example.sso.appa.client;

import java.time.Instant;

/**
 * Result of one outbound call, successful or not, so the UI and the logs can show the same thing.
 *
 * @param label     human-readable description of what was attempted
 * @param at        when it happened
 * @param succeeded whether a 2xx came back
 * @param detail    a one-line summary: the caller identity on success, the failure on error
 * @param response  the parsed body, or {@code null} when the call failed
 */
public record CallOutcome(
        String label,
        Instant at,
        boolean succeeded,
        String detail,
        ResourceResponse response) {

    public static CallOutcome success(String label, ResourceResponse response) {
        return new CallOutcome(label, Instant.now(), true, describe(response), response);
    }

    public static CallOutcome failure(String label, String detail) {
        return new CallOutcome(label, Instant.now(), false, detail, null);
    }

    private static String describe(ResourceResponse response) {
        CallerIdentity caller = response.caller();
        if (caller == null) {
            return "%s returned %d item(s)".formatted(response.application(), size(response));
        }
        String who = caller.serviceAccount()
                ? "application '%s' (service account)".formatted(caller.clientId())
                : "user '%s' via client '%s'".formatted(caller.username(), caller.clientId());
        return "%s accepted us as %s; issuer=%s; roles=%s; %d item(s)".formatted(
                response.application(), who, caller.issuer(), caller.roles(), size(response));
    }

    private static int size(ResourceResponse response) {
        return response.items() == null ? 0 : response.items().size();
    }
}
