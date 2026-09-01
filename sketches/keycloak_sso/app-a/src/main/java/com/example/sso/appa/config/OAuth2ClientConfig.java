package com.example.sso.appa.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.oauth2.client.AuthorizedClientServiceOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProviderBuilder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.InMemoryOAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.AuthenticatedPrincipalOAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.client.web.client.OAuth2ClientHttpRequestInterceptor;
import org.springframework.web.client.RestClient;

/**
 * Outbound OAuth2 wiring - the heart of the machine-to-machine part of this lab.
 *
 * <h2>Why there are two authorized-client managers</h2>
 *
 * Spring Boot auto-configures exactly one {@link OAuth2AuthorizedClientManager}, and it is
 * {@link DefaultOAuth2AuthorizedClientManager}, which is bound to the current servlet request:
 * it looks the request up from {@code RequestContextHolder}. That is fine inside a controller,
 * but a {@code @Scheduled} method runs on a scheduler thread where no request exists, so it
 * fails with:
 *
 * <pre>java.lang.IllegalArgumentException: servletRequest cannot be null</pre>
 *
 * The fix for background work is {@link AuthorizedClientServiceOAuth2AuthorizedClientManager},
 * which stores tokens in an {@link OAuth2AuthorizedClientService} instead of in the HTTP session
 * and therefore needs no request at all.
 *
 * <p>Because declaring <em>any</em> {@code OAuth2AuthorizedClientManager} bean switches Boot's
 * auto-configured one off, both managers are declared explicitly below - one per use case.
 */
@Configuration
public class OAuth2ClientConfig {

    /** Registration used for the interactive browser login (see application.yml). */
    public static final String REGISTRATION_USER_LOGIN = "keycloak-a";
    /** client_credentials registration for Application B - same SSO domain. */
    public static final String REGISTRATION_APP_B_MACHINE = "app-b-m2m";
    /** client_credentials registration for Application C - the other SSO domain. */
    public static final String REGISTRATION_APP_C_MACHINE = "app-c-m2m";

    // ---------------------------------------------------------------------
    // Token storage. Declared explicitly rather than relying on Boot's
    // conditional beans, so the wiring below is unambiguous.
    // ---------------------------------------------------------------------

    @Bean
    OAuth2AuthorizedClientService authorizedClientService(
            ClientRegistrationRepository clientRegistrationRepository) {
        return new InMemoryOAuth2AuthorizedClientService(clientRegistrationRepository);
    }

    @Bean
    OAuth2AuthorizedClientRepository authorizedClientRepository(
            OAuth2AuthorizedClientService authorizedClientService) {
        return new AuthenticatedPrincipalOAuth2AuthorizedClientRepository(authorizedClientService);
    }

    // ---------------------------------------------------------------------
    // Manager 1: application identity, usable from any thread.
    // ---------------------------------------------------------------------

    /**
     * Grants {@code client_credentials} tokens and caches them outside any HTTP session, so the
     * scheduled jobs can use it. Only the client-credentials provider is registered: this manager
     * must never end up handling user tokens.
     */
    @Bean
    OAuth2AuthorizedClientManager machineClientManager(
            ClientRegistrationRepository clientRegistrationRepository,
            OAuth2AuthorizedClientService authorizedClientService) {
        var manager = new AuthorizedClientServiceOAuth2AuthorizedClientManager(
                clientRegistrationRepository, authorizedClientService);
        manager.setAuthorizedClientProvider(OAuth2AuthorizedClientProviderBuilder.builder()
                .clientCredentials()
                .build());
        return manager;
    }

    // ---------------------------------------------------------------------
    // Manager 2: the logged-in user's identity (token relay).
    // ---------------------------------------------------------------------

    /**
     * Serves the token that {@code oauth2Login} stored for the current user, refreshing it when
     * it expires. Request-bound by design, so it must only be used on request threads.
     */
    @Bean
    OAuth2AuthorizedClientManager userClientManager(
            ClientRegistrationRepository clientRegistrationRepository,
            OAuth2AuthorizedClientRepository authorizedClientRepository) {
        var manager = new DefaultOAuth2AuthorizedClientManager(
                clientRegistrationRepository, authorizedClientRepository);
        manager.setAuthorizedClientProvider(OAuth2AuthorizedClientProviderBuilder.builder()
                .authorizationCode()
                .refreshToken()
                .build());
        return manager;
    }

    // ---------------------------------------------------------------------
    // RestClients. One per (target, identity) combination, so which identity
    // is in play is decided once here rather than at every call site.
    // ---------------------------------------------------------------------

    /** Application A calling Application B <b>as itself</b> (same SSO domain). */
    @Bean
    RestClient appBMachineClient(RestClient.Builder builder,
            @Qualifier("machineClientManager") OAuth2AuthorizedClientManager machineClientManager,
            @Value("${lab.app-b.base-url}") String appBBaseUrl) {
        return builder
                .baseUrl(appBBaseUrl)
                .requestInterceptor(machineInterceptor(machineClientManager,
                        REGISTRATION_APP_B_MACHINE, "app-a-scheduler-app-b"))
                .build();
    }

    /** Application A calling Application C <b>as itself</b>, across SSO domains. */
    @Bean
    RestClient appCMachineClient(RestClient.Builder builder,
            @Qualifier("machineClientManager") OAuth2AuthorizedClientManager machineClientManager,
            @Value("${lab.app-c.base-url}") String appCBaseUrl) {
        return builder
                .baseUrl(appCBaseUrl)
                .requestInterceptor(machineInterceptor(machineClientManager,
                        REGISTRATION_APP_C_MACHINE, "app-a-scheduler-app-c"))
                .build();
    }

    /**
     * Application A calling Application B <b>on behalf of the logged-in user</b>. Same target URL
     * as {@link #appBMachineClient}, deliberately: the only difference is whose token is attached.
     */
    @Bean
    RestClient appBUserClient(RestClient.Builder builder,
            @Qualifier("userClientManager") OAuth2AuthorizedClientManager userClientManager,
            @Value("${lab.app-b.base-url}") String appBBaseUrl) {
        var interceptor = new OAuth2ClientHttpRequestInterceptor(userClientManager);
        interceptor.setClientRegistrationIdResolver(request -> REGISTRATION_USER_LOGIN);
        // No principal resolver override: the default reads the SecurityContext, which is exactly
        // what we want - the token belonging to whoever is browsing.
        return builder
                .baseUrl(appBBaseUrl)
                .requestInterceptor(interceptor)
                .build();
    }

    /**
     * Builds an interceptor that fetches a {@code client_credentials} token for
     * {@code registrationId} and attaches it as a bearer token.
     *
     * <p>The principal is pinned to a fixed anonymous token. The interceptor would otherwise fall
     * back to a generic {@code anonymousUser}; naming it makes the entry in the authorized-client
     * store readable and keeps the two schedulers' caches separate.
     */
    private static OAuth2ClientHttpRequestInterceptor machineInterceptor(
            OAuth2AuthorizedClientManager manager, String registrationId, String principalName) {
        var interceptor = new OAuth2ClientHttpRequestInterceptor(manager);
        interceptor.setClientRegistrationIdResolver(request -> registrationId);
        Authentication principal = new AnonymousAuthenticationToken(
                "app-a-machine-identity",
                principalName,
                AuthorityUtils.createAuthorityList("ROLE_SCHEDULER"));
        interceptor.setPrincipalResolver(request -> principal);
        return interceptor;
    }
}
