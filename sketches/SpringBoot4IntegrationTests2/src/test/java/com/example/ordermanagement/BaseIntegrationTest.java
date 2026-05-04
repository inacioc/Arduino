package com.example.ordermanagement;

import com.example.ordermanagement.config.TestJmsConfig;
import com.example.ordermanagement.config.TestSecurityConfig;
import io.zonky.test.db.AutoConfigureEmbeddedDatabase;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * Base class for full-stack integration tests.
 *
 * What it provides:
 *  - Full Spring context (@SpringBootTest)
 *  - Embedded PostgreSQL via io.zonky — no Docker needed
 *  - Embedded Artemis replacing IBM MQ (TestJmsConfig)
 *  - HMAC test JwtDecoder replacing Keycloak (TestSecurityConfig)
 *  - MockMvc auto-configured for HTTP-layer testing
 *  - Each test runs in a transaction that rolls back after the test
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureEmbeddedDatabase(replace = AutoConfigureEmbeddedDatabase.Replace.ANY)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({TestJmsConfig.class, TestSecurityConfig.class})
@Transactional
public abstract class BaseIntegrationTest {
}
