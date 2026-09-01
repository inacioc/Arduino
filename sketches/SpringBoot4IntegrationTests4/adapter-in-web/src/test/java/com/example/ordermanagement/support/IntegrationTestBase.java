package com.example.ordermanagement.support;

import tools.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Base class for all integration tests.
 *
 * Starts the full Spring context against:
 *   - real PostgreSQL  (configured in application-test.yml)
 *   - real IBM MQ      (configured in application-test.yml)
 *   - Keycloak mocked  via SecurityMockMvcRequestPostProcessors.jwt()
 *     (TestSecurityConfig replaces real JwtDecoder to avoid Keycloak startup call)
 *
 * Use @Sql on individual tests to set up / tear down data.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestSecurityConfig.class)
public abstract class IntegrationTestBase {

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;
}
