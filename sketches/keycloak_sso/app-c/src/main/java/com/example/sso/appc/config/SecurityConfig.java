package com.example.sso.appc.config;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

/**
 * One filter chain, because there is only one way into Application C: a bearer token issued by
 * SSO domain C.
 *
 * <p>Note what is <em>absent</em>: no {@code oauth2Login}, no session, no login page, no knowledge
 * of domain A whatsoever. Everything needed to trust the foreign domain sits in two properties -
 * the issuer URI and the expected audience.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/actuator/health").permitAll()
                        // Only the role granted by *this* domain counts. Domain A's roles are
                        // meaningless strings here even if a token somehow carried them.
                        .requestMatchers("/api/**").hasRole("app-c-api-reader")
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())))
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .csrf(csrf -> csrf.disable())
                .build();
    }

    private static JwtAuthenticationConverter jwtAuthenticationConverter() {
        var converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> authorities(jwt.getClaims()));
        return converter;
    }

    /**
     * Keycloak nests realm roles under {@code realm_access.roles}; Spring Security's stock
     * converters only read flat claims such as {@code scope}. Without this, a valid token produces
     * zero authorities and every request is a 403.
     */
    static Collection<GrantedAuthority> authorities(Map<String, Object> claims) {
        return realmRoles(claims).stream()
                .map(role -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + role))
                .toList();
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
