package com.example.ordermanagement.support;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.example.ordermanagement.config.TestSecurityConfig;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * Generates signed JWTs for tests that make real HTTP calls
 * (i.e., @SpringBootTest with WebEnvironment.RANDOM_PORT).
 *
 * For MockMvc tests prefer: SecurityMockMvcRequestPostProcessors.jwt()
 */
public final class JwtTestUtil {

    private JwtTestUtil() {}

    public static String generateToken(String subject, List<String> roles) {
        try {
            var signer = new MACSigner(TestSecurityConfig.TEST_SECRET.getBytes(StandardCharsets.UTF_8));

            var claims = new JWTClaimsSet.Builder()
                    .subject(subject)
                    .issuer("http://localhost:9999/test-realm")
                    .expirationTime(new Date(System.currentTimeMillis() + 3_600_000))
                    .issueTime(new Date())
                    .claim("realm_access", Map.of("roles", roles))
                    .build();

            var jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
            jwt.sign(signer);
            return jwt.serialize();
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate test JWT", e);
        }
    }

    public static String userToken(String subject) {
        return generateToken(subject, List.of("user"));
    }

    public static String adminToken(String subject) {
        return generateToken(subject, List.of("user", "admin"));
    }
}
