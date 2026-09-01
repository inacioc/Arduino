package com.example.sso.appb.config;

import java.util.HashSet;
import java.util.Set;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.mapping.GrantedAuthoritiesMapper;
import org.springframework.security.oauth2.core.oidc.user.OidcUserAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Two filter chains, because Application B is entered in two completely different ways.
 *
 * <p>The order matters. Spring Security tries each chain in order and uses the first whose
 * {@code securityMatcher} matches, so the narrow {@code /api/**} chain must come first. Otherwise
 * the catch-all browser chain would grab API requests and answer a bearer-token call with a 302
 * redirect to the Keycloak login page - the classic "my API returns a redirect instead of 401".
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /**
     * Chain 1 - the API. Stateless bearer-token validation: no session, no redirect, no CSRF token
     * (there is no browser form to protect, and the caller is not cookie-authenticated).
     *
     * <p>{@code /api/**} accepts <em>either</em> role on purpose:
     * <ul>
     *   <li>{@code app-b-api-reader} - Application A's service account, i.e. a machine;</li>
     *   <li>{@code app-b-user} - a human, which is what makes the token-relay flow work.</li>
     * </ul>
     * One endpoint serving both makes the two identity models directly comparable.
     */
    @Bean
    @Order(1)
    SecurityFilterChain apiFilterChain(HttpSecurity http) throws Exception {
        return http
                .securityMatcher("/api/**")
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/api/**").hasAnyRole("app-b-api-reader", "app-b-user"))
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())))
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .csrf(csrf -> csrf.disable())
                .build();
    }

    /**
     * Chain 2 - the browser application, and the half that produces the SSO effect. It redirects to
     * the same realm Application A uses, so Keycloak recognises its own session cookie and issues
     * tokens without asking for a password again.
     */
    @Bean
    @Order(2)
    SecurityFilterChain browserFilterChain(HttpSecurity http) throws Exception {
        return http
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/", "/error", "/css/**", "/actuator/health").permitAll()
                        .requestMatchers("/ui/**").hasRole("app-b-user")
                        .anyRequest().authenticated())
                .oauth2Login(Customizer.withDefaults())
                .logout(logout -> logout
                        .logoutSuccessUrl("/")
                        .invalidateHttpSession(true)
                        .clearAuthentication(true))
                .build();
    }

    /** Bearer-token path: {@code realm_access.roles} -> {@code ROLE_*} authorities. */
    private static JwtAuthenticationConverter jwtAuthenticationConverter() {
        var converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> KeycloakRealmRoles.authorities(jwt.getClaims()));
        return converter;
    }

    /** Browser path: same mapping, but the roles come from the logged-in user's ID token. */
    @Bean
    GrantedAuthoritiesMapper userAuthoritiesMapper() {
        return authorities -> {
            Set<GrantedAuthority> mapped = new HashSet<>(authorities);
            authorities.stream()
                    .filter(OidcUserAuthority.class::isInstance)
                    .map(OidcUserAuthority.class::cast)
                    .forEach(authority -> mapped.addAll(
                            KeycloakRealmRoles.authorities(authority.getIdToken().getClaims())));
            return mapped;
        };
    }
}
