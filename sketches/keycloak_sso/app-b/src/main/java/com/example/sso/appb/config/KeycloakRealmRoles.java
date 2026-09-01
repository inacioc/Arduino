package com.example.sso.appb.config;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

/**
 * Reads Keycloak's realm roles out of the nested {@code realm_access.roles} claim.
 *
 * <p>This tiny class exists because of a mismatch that catches nearly everyone: Keycloak publishes
 * roles as
 *
 * <pre>{ "realm_access": { "roles": ["app-b-user"] } }</pre>
 *
 * while Spring Security's stock converters only look at flat claims such as {@code scope}. Left
 * alone, a perfectly valid token yields zero authorities, and every secured endpoint answers 403
 * even though authentication clearly succeeded.
 */
final class KeycloakRealmRoles {

    private KeycloakRealmRoles() {
    }

    /** Extracts the role names, or an empty list if the claim is absent or malformed. */
    @SuppressWarnings("unchecked")
    static List<String> from(Map<String, Object> claims) {
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

    /** Same thing, prefixed with {@code ROLE_} so {@code hasRole("app-b-user")} matches. */
    static Collection<GrantedAuthority> authorities(Map<String, Object> claims) {
        return from(claims).stream()
                .map(role -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + role))
                .toList();
    }
}
