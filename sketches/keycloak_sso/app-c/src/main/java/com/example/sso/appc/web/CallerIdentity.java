package com.example.sso.appc.web;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.springframework.security.oauth2.jwt.Jwt;

/**
 * What Application C can prove about its caller, read from the validated token. Echoed back so the
 * caller can see which identity - and crucially which <em>issuer</em> - was accepted.
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
                username != null && clientId != null
                        && username.equals("service-account-" + clientId));
    }

    @SuppressWarnings("unchecked")
    private static List<String> realmRoles(Map<String, Object> claims) {
        Object realmAccess = claims.get("realm_access");
        if (!(realmAccess instanceof Map<?, ?> map)) {
            return List.of();
        }
        Object roles = map.get("roles");
        if (!(roles instanceof Collection<?> roleList)) {
            return List.of();
        }
        return List.copyOf((Collection<String>) roleList);
    }
}
