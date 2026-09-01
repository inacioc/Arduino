package com.example.sso.appb.web;

import java.util.List;
import java.util.Map;

import org.springframework.security.oauth2.jwt.Jwt;

/**
 * What Application B can prove about whoever just called it, taken from the validated access token.
 *
 * <p>Echoing this back to the caller is the whole trick that makes this lab observable: instead of
 * reasoning about which identity <em>should</em> have been used, you can read which one actually
 * arrived.
 */
public record CallerIdentity(
        String issuer,
        String subject,
        String username,
        String clientId,
        List<String> audience,
        List<String> roles,
        boolean serviceAccount) {

    public static CallerIdentity of(Jwt jwt) {
        String clientId = jwt.getClaimAsString("azp");
        String username = jwt.getClaimAsString("preferred_username");
        return new CallerIdentity(
                jwt.getIssuer() == null ? null : jwt.getIssuer().toString(),
                jwt.getSubject(),
                username,
                clientId,
                jwt.getAudience(),
                realmRoles(jwt.getClaims()),
                isServiceAccount(username, clientId));
    }

    /**
     * Keycloak backs every service account with a synthetic user called
     * {@code service-account-<clientId>}, so a machine token is not distinguished by a missing
     * username but by that naming convention.
     */
    private static boolean isServiceAccount(String username, String clientId) {
        return username != null && clientId != null
                && username.equals("service-account-" + clientId);
    }

    @SuppressWarnings("unchecked")
    private static List<String> realmRoles(Map<String, Object> claims) {
        Object realmAccess = claims.get("realm_access");
        if (!(realmAccess instanceof Map<?, ?> map)) {
            return List.of();
        }
        Object roles = map.get("roles");
        if (!(roles instanceof java.util.Collection<?> roleList)) {
            return List.of();
        }
        return List.copyOf((java.util.Collection<String>) roleList);
    }
}
