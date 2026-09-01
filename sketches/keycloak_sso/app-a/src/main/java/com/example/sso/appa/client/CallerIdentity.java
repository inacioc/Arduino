package com.example.sso.appa.client;

import java.util.List;

/**
 * How the callee saw us. Applications B and C both echo this back so the identity actually used
 * for a call is observable instead of guessed.
 *
 * @param issuer         which SSO domain minted the token ({@code iss})
 * @param subject        the token subject ({@code sub})
 * @param username       {@code preferred_username}, absent for pure machine tokens
 * @param clientId       {@code azp} - the OAuth2 client the token was issued to
 * @param audience       {@code aud} - who the token is meant for
 * @param roles          realm roles carried by the token
 * @param serviceAccount true when the token represents an application rather than a human
 */
public record CallerIdentity(
        String issuer,
        String subject,
        String username,
        String clientId,
        List<String> audience,
        List<String> roles,
        boolean serviceAccount) {
}
