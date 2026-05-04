package com.example.orders;

import dasniko.testcontainers.keycloak.KeycloakContainer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Base class for all integration tests.
 *
 * Boots a real PostgreSQL, Kafka and Keycloak via Testcontainers once for the entire
 * test suite (static containers → shared between test classes that extend this).
 *
 * Each concrete test class uses @SpringBootTest to load a full application context
 * wired against the running containers.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Testcontainers
public abstract class AbstractIntegrationTest {

    // ── Containers (started once, shared across all subclasses) ──────────────

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:17-alpine")
                    .withDatabaseName("orders_test")
                    .withUsername("test")
                    .withPassword("test");

    @Container
    static final KafkaContainer KAFKA =
            new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.7.0"));

    @Container
    static final KeycloakContainer KEYCLOAK =
            new KeycloakContainer("quay.io/keycloak/keycloak:25.0.2")
                    .withRealmImportFile("keycloak/test-realm.json");

    // ── Wire container endpoints into Spring's property sources ─────────────

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        // PostgreSQL
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);

        // Kafka
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);

        // Keycloak JWT issuer
        registry.add("spring.security.oauth2.resourceserver.jwt.issuer-uri",
                () -> KEYCLOAK.getAuthServerUrl() + "/realms/orders-realm");
    }

    // ── Helpers available to all test classes ────────────────────────────────

    @LocalServerPort
    protected int serverPort;

    @Autowired
    protected WebTestClient webTestClient;

    /**
     * Retrieves a JWT access token from Keycloak using the Resource Owner Password flow.
     * Only valid in tests — never in production.
     */
    protected String getAccessToken(String username, String password) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "password");
        form.add("client_id", "orders-app");
        form.add("client_secret", "test-client-secret");
        form.add("username", username);
        form.add("password", password);

        return WebTestClient
                .bindToServer()
                .baseUrl(KEYCLOAK.getAuthServerUrl())
                .build()
                .post()
                .uri("/realms/orders-realm/protocol/openid-connect/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(BodyInserters.fromFormData(form))
                .exchange()
                .expectStatus().isOk()
                .expectBody(TokenResponse.class)
                .returnResult()
                .getResponseBody()
                .accessToken();
    }

    record TokenResponse(
            com.fasterxml.jackson.annotation.JsonProperty("access_token") String accessToken
    ) {}
}
