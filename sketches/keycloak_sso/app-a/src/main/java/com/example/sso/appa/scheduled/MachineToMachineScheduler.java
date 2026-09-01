package com.example.sso.appa.scheduled;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.example.sso.appa.client.CallOutcome;
import com.example.sso.appa.client.DownstreamCaller;

/**
 * The machine-to-machine part of the scenario: no browser, no user, no session.
 *
 * <p>Both jobs authenticate with the {@code client_credentials} grant. Application A presents its
 * own client id and secret to a token endpoint, receives an access token that represents
 * <em>the application</em>, and calls the downstream API with it. The second job does the same
 * thing against a completely different identity provider using a completely different set of
 * credentials - which is all "another SSO domain" amounts to from the caller's point of view.
 *
 * <p>Token acquisition, caching and renewal are handled by the authorized-client manager wired in
 * {@code OAuth2ClientConfig}; there is deliberately no token handling code here.
 */
@Component
public class MachineToMachineScheduler {

    private static final Logger log = LoggerFactory.getLogger(MachineToMachineScheduler.class);

    private final DownstreamCaller caller;
    private final Map<String, CallOutcome> lastOutcomes = new ConcurrentHashMap<>();

    public MachineToMachineScheduler(DownstreamCaller caller) {
        this.caller = caller;
    }

    /** Same SSO domain as Application A's human users, but a separate machine client. */
    @Scheduled(initialDelayString = "${lab.app-b.initial-delay-ms}",
               fixedDelayString = "${lab.app-b.schedule-ms}")
    public void callApplicationB() {
        record("app-b", caller.fetchAppBReportsAsMachine());
    }

    /** Different Keycloak instance, different realm, different credentials. */
    @Scheduled(initialDelayString = "${lab.app-c.initial-delay-ms}",
               fixedDelayString = "${lab.app-c.schedule-ms}")
    public void callApplicationC() {
        record("app-c", caller.fetchAppCInventoryAsMachine());
    }

    private void record(String key, CallOutcome outcome) {
        lastOutcomes.put(key, outcome);
        if (outcome.succeeded()) {
            log.info("[scheduled -> {}] OK  | {}", key, outcome.detail());
        }
        else {
            log.warn("[scheduled -> {}] FAIL | {} | attempted: {}",
                    key, outcome.detail(), outcome.label());
        }
    }

    /** Last result per target, so the UI can show what the background jobs have been doing. */
    public Map<String, CallOutcome> lastOutcomes() {
        return Map.copyOf(lastOutcomes);
    }
}
