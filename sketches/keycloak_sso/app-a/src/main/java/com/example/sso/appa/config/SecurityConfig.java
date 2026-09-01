package com.example.sso.appa.config;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.authority.mapping.GrantedAuthoritiesMapper;
import org.springframework.security.oauth2.client.oidc.web.logout.OidcClientInitiatedLogoutSuccessHandler;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.oidc.user.OidcUserAuthority;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Browser-facing security for Application A.
 *
 * <p>Everything under {@code /ui/**} requires the realm role {@code app-a-user}. That is the
 * whole point of the {@code bob} test user: he authenticates successfully against SSO domain A
 * but is missing {@code app-b-user}, so he is a legitimate user of A and a stranger to B.
 * Authentication and authorization are genuinely separate concerns here.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http,
            ClientRegistrationRepository clientRegistrationRepository) throws Exception {
        return http
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/", "/error", "/css/**", "/actuator/health").permitAll()
                        .requestMatchers("/ui/**").hasRole("app-a-user")
                        .anyRequest().authenticated())
                .oauth2Login(login -> login
                        // Land on the profile page after a successful login rather than on "/".
                        .defaultSuccessUrl("/ui/profile", true))
                .logout(logout -> logout
                        // Local logout: clears this application's session only. The Keycloak SSO
                        // cookie survives, so clicking "login" again signs you straight back in
                        // without a password prompt - a good way to *see* that the SSO session
                        // lives in Keycloak and not in the application.
                        //
                        // To upgrade to RP-initiated single logout (logging out of Application A
                        // also ends the Keycloak session, so Application B demands a fresh
                        // login), swap the line below for:
                        //
                        //   .logoutSuccessHandler(oidcLogoutSuccessHandler(clientRegistrationRepository))
                        //
                        // and see oidcLogoutSuccessHandler(..) at the bottom of this class.
                        .logoutSuccessUrl("/")
                        .invalidateHttpSession(true)
                        .clearAuthentication(true))
                .build();
    }

    /**
     * Keycloak realm roles arrive as a nested claim, {@code realm_access.roles}, which Spring
     * Security knows nothing about. Without this mapper a logged-in user has an authenticated
     * session and <em>zero</em> authorities, so every {@code hasRole(..)} check fails and you get
     * a puzzling 403 right after a successful login.
     *
     * <p>The roles only reach the ID token because each web client in the realm carries a realm-roles
     * protocol mapper with {@code id.token.claim=true}; Keycloak does not put them there by default.
     */
    @Bean
    GrantedAuthoritiesMapper userAuthoritiesMapper() {
        return authorities -> {
            Set<GrantedAuthority> mapped = new HashSet<>(authorities);
            authorities.stream()
                    .filter(OidcUserAuthority.class::isInstance)
                    .map(OidcUserAuthority.class::cast)
                    .forEach(authority -> mapped.addAll(
                            realmRoles(authority.getIdToken().getClaims())));
            return mapped;
        };
    }

    /**
     * Reads {@code realm_access.roles} and prefixes each role with {@code ROLE_}, which is what
     * {@code hasRole("app-a-user")} looks for.
     */
    @SuppressWarnings("unchecked")
    static Collection<GrantedAuthority> realmRoles(Map<String, Object> claims) {
        Object realmAccess = claims.get("realm_access");
        if (!(realmAccess instanceof Map<?, ?> map)) {
            return List.of();
        }
        Object roles = map.get("roles");
        if (!(roles instanceof Collection<?> roleList)) {
            return List.of();
        }
        return ((Collection<String>) roleList).stream()
                .map(role -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + role))
                .toList();
    }

    /**
     * Not wired in by default - see the comment in the logout block above. Kept here so switching
     * to RP-initiated single logout is a one-line change.
     */
    @SuppressWarnings("unused")
    private static OidcClientInitiatedLogoutSuccessHandler oidcLogoutSuccessHandler(
            ClientRegistrationRepository clientRegistrationRepository) {
        var handler = new OidcClientInitiatedLogoutSuccessHandler(clientRegistrationRepository);
        // Must be registered as a valid post-logout redirect URI on the app-a-web client.
        handler.setPostLogoutRedirectUri("http://localhost:8081/");
        return handler;
    }
}
