# Order Management — Spring Boot 4 Integration Tests

A reference project demonstrating complete integration testing on a **hexagonal architecture** application using the **Spring Boot 4 test ecosystem only** — no Testcontainers, no Docker, no external infrastructure required to run the test suite.

---

## Technology Stack

| Layer | Technology |
|---|---|
| Language | Java 25 |
| Framework | Spring Boot 4 |
| Database | PostgreSQL (embedded in tests) |
| Authentication | Keycloak as OAuth2/JWT Resource Server |
| Messaging | IBM MQ (replaced by embedded Artemis in tests) |
| External REST | RestClient + WireMock in tests |
| DB Migrations | Flyway |

---

## Project Structure

```
src/
├── main/java/com/example/ordermanagement/
│   │
│   ├── domain/                          ← Pure Java. Zero framework annotations.
│   │   ├── model/
│   │   │   ├── Order.java               Aggregate root with business rules
│   │   │   ├── OrderItem.java           Value object (Java record)
│   │   │   └── OrderStatus.java         Enum: PENDING → CONFIRMED → DELIVERED
│   │   └── port/
│   │       ├── in/                      Driving ports (use-case interfaces)
│   │       │   ├── CreateOrderUseCase.java
│   │       │   ├── GetOrderUseCase.java
│   │       │   └── UpdateOrderStatusUseCase.java
│   │       └── out/                     Driven ports (repository/external interfaces)
│   │           ├── OrderRepositoryPort.java
│   │           ├── NotificationPort.java
│   │           └── PricingServicePort.java
│   │
│   ├── application/service/             ← Orchestrates the domain via ports.
│   │   └── OrderApplicationService.java Implements all three use-case interfaces.
│   │
│   └── infrastructure/                  ← Framework code. Implements the out-ports.
│       ├── adapter/in/web/              HTTP driving adapter (Spring MVC)
│       │   ├── OrderController.java
│       │   ├── GlobalExceptionHandler.java   ProblemDetail (RFC 7807)
│       │   └── dto/
│       │       ├── CreateOrderRequest.java   (record + Bean Validation)
│       │       └── OrderResponse.java        (record)
│       ├── adapter/out/persistence/     JPA driven adapter
│       │   ├── entity/
│       │   │   ├── OrderJpaEntity.java
│       │   │   └── OrderItemJpaEntity.java
│       │   ├── OrderJpaRepository.java  (package-private — not exposed to domain)
│       │   └── OrderPersistenceAdapter.java  Implements OrderRepositoryPort
│       ├── adapter/out/messaging/       IBM MQ driven adapter
│       │   ├── IbmMqOrderPublisher.java Implements NotificationPort
│       │   ├── IbmMqOrderConsumer.java  @JmsListener for inbound events
│       │   └── dto/OrderEventMessage.java
│       ├── adapter/out/external/        REST driven adapter (pricing service)
│       │   ├── PricingServiceAdapter.java    Implements PricingServicePort
│       │   └── dto/
│       │       ├── PricingRequest.java
│       │       └── PricingResponse.java
│       └── config/
│           ├── SecurityConfig.java      JWT resource server + role-based auth
│           ├── KeycloakJwtConverter.java Extracts realm_access.roles from JWT
│           ├── JmsConfig.java           IBM MQ config (@Profile("!test"))
│           └── RestClientConfig.java    RestClient.Builder bean
│
├── main/resources/
│   ├── application.yml
│   └── db/migration/V1__create_orders_tables.sql
│
└── test/java/com/example/ordermanagement/
    ├── BaseIntegrationTest.java         Shared base: @SpringBootTest + embedded Postgres + Artemis
    ├── config/
    │   ├── TestJmsConfig.java           Embedded Artemis replacing IBM MQ
    │   └── TestSecurityConfig.java      HMAC JwtDecoder replacing Keycloak
    ├── support/
    │   ├── TestDataBuilder.java         Test fixtures builder
    │   └── JwtTestUtil.java             Signed JWT generator for real HTTP tests
    └── integration/
        ├── web/
        │   ├── OrderControllerIT.java        Full stack (HTTP → DB)
        │   └── OrderControllerSliceIT.java   @WebMvcTest (web layer only)
        ├── persistence/
        │   └── OrderPersistenceIT.java       @DataJpaTest (JPA layer only)
        ├── messaging/
        │   └── OrderMessagingIT.java         JMS publish/consume
        ├── external/
        │   └── PricingServiceIT.java         WireMock HTTP stubs
        └── service/
            └── OrderApplicationServiceIT.java  Full service + real DB
```

---

## Hexagonal Architecture Principles Applied

The hexagonal architecture (Ports & Adapters) enforces a strict dependency rule:

```
Domain ← Application ← Infrastructure
```

- **Domain** (`domain/`) has no Spring annotations, no JPA annotations, no framework imports. It is pure Java business logic testable with plain unit tests.
- **Application** (`application/service/`) depends only on the domain and its port interfaces. It never knows whether data comes from Postgres or an in-memory map.
- **Infrastructure** (`infrastructure/`) depends on both domain (implements ports) and the framework (Spring, JPA, JMS). It is the only layer that changes when you swap IBM MQ for Kafka, or Postgres for Oracle.

> **Note on "imperfect hexagonal":** In this codebase `OrderJpaRepository` is package-private so only `OrderPersistenceAdapter` can see it. The domain never touches JPA. The service never touches HTTP. That boundary is enforced by package visibility, not by separate Maven modules.

---

## Test Tier Strategy

Five independent test tiers, each with a clear scope and speed trade-off:

```
     Slowest                                                    Fastest
     Most confidence                                    Most isolation
     ┌──────────────────────────────────────────────────────────────────┐
     │  @SpringBootTest           @SpringBootTest        @WebMvcTest   │
     │  (full stack)              (service/msg/ext)      (web slice)   │
     │                                                                  │
     │ OrderControllerIT   OrderApplicationServiceIT  OrderController  │
     │                     OrderMessagingIT            SliceIT         │
     │                     PricingServiceIT                            │
     │                                                                  │
     │                     @DataJpaTest                               │
     │                     OrderPersistenceIT                         │
     └──────────────────────────────────────────────────────────────────┘
```

### Tier 1 — `@WebMvcTest` (fastest)

**File:** [OrderControllerSliceIT.java](src/test/java/com/example/ordermanagement/integration/web/OrderControllerSliceIT.java)

Loads **only** the web layer: controller, security filter chain, exception handler. All use-case ports are replaced by `@MockitoBean`. No database, no JMS, no external services.

**Best for:**
- HTTP contract verification (status codes, JSON structure)
- Security rule verification (which role can call which endpoint)
- Request validation (`@Valid`, `@NotBlank`, `@Min`)
- Exception-to-HTTP-status mapping

```java
@WebMvcTest(OrderController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class OrderControllerSliceIT {

    @MockitoBean
    private CreateOrderUseCase createOrderUseCase;

    @Test
    void confirmOrder_whenUserRole_returns403() throws Exception {
        mockMvc.perform(patch("/api/v1/orders/{id}/confirm", orderId)
                        .with(jwt().jwt(j -> j
                                .claim("realm_access", Map.of("roles", List.of("user"))))))
                .andExpect(status().isForbidden());
    }
}
```

---

### Tier 2 — `@DataJpaTest` (fast, real SQL)

**File:** [OrderPersistenceIT.java](src/test/java/com/example/ordermanagement/integration/persistence/OrderPersistenceIT.java)

Loads **only** JPA beans + Flyway. All other beans (web, JMS, security) are excluded. Uses **real embedded PostgreSQL** — same dialect, same constraints, same SQL behaviour as production.

**Best for:**
- Custom JPQL / native queries
- Schema constraint verification (NOT NULL, CHECK, FK cascade)
- Mapping correctness (domain ↔ JPA entity round-trips)
- PostgreSQL-specific features (UUID, TIMESTAMPTZ, JSONB)

```java
@DataJpaTest
@AutoConfigureEmbeddedDatabase(replace = AutoConfigureEmbeddedDatabase.Replace.ANY)
@Import(OrderPersistenceAdapter.class)
class OrderPersistenceIT {

    @Test
    void save_updatedStatus_persistsStatusChange() {
        Order saved = adapter.save(buildOrder("cust-004"));
        saved.confirm();
        adapter.save(saved);

        assertThat(adapter.findById(saved.getId()).get().getStatus())
                .isEqualTo(OrderStatus.CONFIRMED);
    }
}
```

> Each test runs inside a transaction that auto-rolls back — the database is clean for every test method.

---

### Tier 3 — `@SpringBootTest` + WireMock (external REST)

**File:** [PricingServiceIT.java](src/test/java/com/example/ordermanagement/integration/external/PricingServiceIT.java)

Loads the full application context but starts it with `WebEnvironment.NONE` (no HTTP server needed). WireMock starts on a random local port and its URL is injected into Spring's environment via `@DynamicPropertySource` before the context initialises.

**Best for:**
- Verifying the `RestClient` request structure (body, headers, URL)
- Resilience testing: 5xx responses, timeouts, network errors
- Fallback / circuit-breaker behaviour

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class PricingServiceIT {

    static WireMockServer wireMock = new WireMockServer(options().dynamicPort());

    @DynamicPropertySource
    static void overrideUrl(DynamicPropertyRegistry r) {
        r.add("external.pricing-service.base-url", wireMock::baseUrl);
    }

    @Test
    void calculateTotalPrice_serviceReturns500_fallsBackToLocalCalculation() {
        wireMock.stubFor(post(urlEqualTo("/api/pricing/calculate"))
                .willReturn(serverError()));

        BigDecimal result = pricingServiceAdapter.calculateTotalPrice(sampleOrder());

        assertThat(result).isGreaterThan(BigDecimal.ZERO); // fallback applied
    }
}
```

---

### Tier 4 — `@SpringBootTest` + embedded Artemis (JMS / IBM MQ)

**File:** [OrderMessagingIT.java](src/test/java/com/example/ordermanagement/integration/messaging/OrderMessagingIT.java)

Full Spring context with the real `IbmMqOrderPublisher` and `IbmMqOrderConsumer` wired up, but using an **in-process Artemis JMS broker** instead of IBM MQ. The same `JmsTemplate` and `@JmsListener` APIs are used — only the `ConnectionFactory` implementation differs.

**Best for:**
- Verifying that the correct queue name is used
- Verifying message content (all fields present, correct values)
- Verifying `@JmsListener` method is triggered for inbound messages
- Async message processing using Awaitility (no `Thread.sleep`)

```java
@Test
void sendOrderCreatedNotification_publishesMessageToCorrectQueue() {
    publisher.sendOrderCreatedNotification(order);

    OrderEventMessage received = (OrderEventMessage) jmsTemplate
            .receiveAndConvert(IbmMqOrderPublisher.QUEUE_ORDER_CREATED);

    assertThat(received.orderId()).isEqualTo(order.getId());
    assertThat(received.eventType()).isEqualTo("ORDER_CREATED");
}
```

---

### Tier 5 — `@SpringBootTest` full stack (slowest, most confidence)

**Files:** [OrderControllerIT.java](src/test/java/com/example/ordermanagement/integration/web/OrderControllerIT.java) and [OrderApplicationServiceIT.java](src/test/java/com/example/ordermanagement/integration/service/OrderApplicationServiceIT.java)

Full application context: HTTP → Controller → Service → Repository → **embedded PostgreSQL**. Uses both the classic `MockMvc` and the new **`MockMvcTester`** fluent API (Spring Boot 4).

**Best for:**
- End-to-end flow verification across all layers
- Cross-cutting concerns (transactions, event publishing after save)
- Regression tests for critical business flows

```java
// Classic MockMvc style
mockMvc.perform(post("/api/v1/orders")
                .contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .with(jwt().jwt(j -> j.subject("user-123")
                        .claim("realm_access", Map.of("roles", List.of("user"))))))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.status").value("PENDING"));

// New MockMvcTester fluent API (Spring Boot 4)
assertThat(mvc.get().uri("/api/v1/orders")
                .with(jwt().jwt(j -> j.subject("user-tester")
                        .claim("realm_access", Map.of("roles", List.of("user"))))))
        .hasStatus2xxSuccessful()
        .bodyJson()
        .isArray();
```

---

## Solving Each Infrastructure Constraint Without Containers

### PostgreSQL — `io.zonky.test:embedded-database-spring-test`

**Problem:** Tests need real PostgreSQL (H2 lacks UUID types, `TIMESTAMPTZ`, Flyway PostgreSQL scripts).  
**Solution:** `@AutoConfigureEmbeddedDatabase` downloads and starts a real PostgreSQL process locally — no Docker, no network.

```java
// In BaseIntegrationTest.java and OrderPersistenceIT.java
@AutoConfigureEmbeddedDatabase(replace = AutoConfigureEmbeddedDatabase.Replace.ANY)
```

- `Replace.ANY` tells the library to replace **any** `DataSource` bean, regardless of how it was configured.
- Flyway migrations run on it exactly as in production.
- Each test method runs in an auto-rollback transaction → clean state, no test pollution.
- Add the OS-specific binary to `pom.xml` (linux-amd64, windows-amd64, darwin-amd64).

---

### IBM MQ — Embedded Apache Artemis

**Problem:** IBM MQ requires an external broker process.  
**Solution:** IBM MQ auto-configuration is excluded in the `test` profile. `TestJmsConfig` starts an in-process Artemis server on the `vm://0` transport and exposes it as a `@Primary ConnectionFactory`.

**Why Artemis works as a drop-in replacement:**
- Both implement the **JMS 3.0** API (Jakarta Messaging).
- `JmsTemplate.convertAndSend()` works identically.
- `@JmsListener` works identically.
- Only the `ConnectionFactory` implementation changes — everything else stays the same.

```java
// JmsConfig.java — production only
@Configuration
@Profile("!test")       // ← never loaded in tests
public class JmsConfig { ... }

// TestJmsConfig.java — test only
@TestConfiguration
@Profile("test")
public class TestJmsConfig {

    @Bean(destroyMethod = "stop")
    public ActiveMQServer embeddedArtemisServer() throws Exception {
        var config = new ConfigurationImpl();
        config.setSecurityEnabled(false);
        config.setPersistenceEnabled(false);   // in-memory only
        config.addAcceptorConfiguration("in-vm", "vm://0");
        var server = ActiveMQServers.newActiveMQServer(config);
        server.start();
        return server;
    }

    @Bean @Primary
    public ConnectionFactory testConnectionFactory(ActiveMQServer server) {
        return new ActiveMQConnectionFactory("vm://0");
    }
}
```

The IBM MQ auto-configuration is also excluded in `application-test.yml`:
```yaml
spring:
  autoconfigure:
    exclude:
      - com.ibm.mq.spring.boot.MQAutoConfiguration
```

---

### Keycloak / JWT — Spring Security Test + HMAC decoder

**Problem:** Tests cannot reach a running Keycloak instance.  
**Two solutions depending on test tier:**

**Option A — `MockMvc` tests (preferred):**  
`SecurityMockMvcRequestPostProcessors.jwt()` directly injects a `JwtAuthenticationToken` into the security context, bypassing the JWT decoder entirely. Keycloak roles are set via the `realm_access` claim:

```java
.with(jwt().jwt(j -> j
        .subject("user-123")
        .claim("realm_access", Map.of("roles", List.of("user", "admin")))))
```

**Option B — Real HTTP tests (`WebEnvironment.RANDOM_PORT`):**  
`TestSecurityConfig` replaces the `JwtDecoder` bean with an HMAC-SHA256 decoder keyed to a fixed test secret. `JwtTestUtil` mints signed tokens the decoder will accept:

```java
// TestSecurityConfig.java
@Bean
public JwtDecoder testJwtDecoder() {
    byte[] keyBytes = TEST_SECRET.getBytes(StandardCharsets.UTF_8);
    return NimbusJwtDecoder.withSecretKey(new SecretKeySpec(keyBytes, "HmacSHA256")).build();
}

// JwtTestUtil.java
String token = JwtTestUtil.adminToken("admin-user-id");
restTemplate.exchange(..., HttpHeaders with "Authorization: Bearer " + token, ...);
```

---

### External REST (pricing service) — WireMock

**Problem:** The pricing service is a third-party REST API not available in the test environment.  
**Solution:** WireMock starts a local HTTP server on a random port. `@DynamicPropertySource` injects the base URL before Spring creates the `PricingServiceAdapter` bean:

```java
static WireMockServer wireMock = new WireMockServer(options().dynamicPort());

@DynamicPropertySource
static void overrideUrl(DynamicPropertyRegistry registry) {
    registry.add("external.pricing-service.base-url", wireMock::baseUrl);
}

@Test
void stubbedSuccessCase() {
    wireMock.stubFor(post(urlEqualTo("/api/pricing/calculate"))
            .willReturn(okJson("""{"totalPrice": 99.99, "currency": "USD"}""")));

    BigDecimal price = pricingServiceAdapter.calculateTotalPrice(order);

    assertThat(price).isEqualByComparingTo("99.99");
    wireMock.verify(1, postRequestedFor(urlEqualTo("/api/pricing/calculate")));
}
```

---

## Spring Boot 4 Testing APIs Used

### `@MockitoBean` / `@MockitoSpyBean`

Spring Boot 4 replacements for the deprecated `@MockBean` / `@SpyBean`. They register a Mockito mock/spy as a Spring bean in the application context for the duration of the test.

```java
// Replaces the real PricingServiceAdapter with a Mockito mock
@MockitoBean
private PricingServicePort pricingServicePort;

// Wraps the real IbmMqOrderPublisher with a Mockito spy
// (real method executes AND calls can be verified)
@MockitoSpyBean
private NotificationPort notificationPort;
```

**When to use `@MockitoBean` vs constructor injection:**  
Use `@MockitoBean` when you need to replace a Spring-managed bean (e.g., one that involves transactions, proxies, or AOP). For pure unit tests with no Spring context, prefer plain `Mockito.mock()` injected via constructor.

### `MockMvcTester`

New fluent API available since Spring Boot 3.4, standard in Spring Boot 4. Replaces `MockMvc` with an AssertJ-based API that avoids checked exceptions:

```java
// Old MockMvc style (still fully supported)
mockMvc.perform(get("/api/v1/orders/{id}", orderId))
       .andExpect(status().isOk())
       .andExpect(jsonPath("$.status").value("CONFIRMED"));

// New MockMvcTester style (Spring Boot 4)
assertThat(mvc.get().uri("/api/v1/orders/{id}", orderId)
              .with(jwt()))
        .hasStatusOk()
        .bodyJson()
        .extractingPath("$.status").isEqualTo("CONFIRMED");
```

### `ProblemDetail` (RFC 7807)

Spring 6 / Spring Boot 3+ includes built-in RFC 7807 Problem Detail support. The `GlobalExceptionHandler` returns structured error bodies:

```json
{
  "type": "about:blank",
  "title": "Order Not Found",
  "status": 404,
  "detail": "Order not found: 123e4567-e89b-12d3-a456-426614174000"
}
```

Tests verify error semantics via `jsonPath("$.title")` and `jsonPath("$.violations")`.

---

## Running the Tests

```bash
# Run all integration tests
mvn verify

# Run a specific test class
mvn test -Dtest=OrderControllerSliceIT

# Run only the fast slices (no DB startup)
mvn test -Dtest="OrderControllerSliceIT"

# Run all @DataJpaTest (fast, real Postgres)
mvn test -Dtest="OrderPersistenceIT"
```

> On first run, the embedded-postgres library downloads the PostgreSQL binaries (~20 MB). Subsequent runs use the cached version.

---

## Key Dependencies Explained

```xml
<!-- Real PostgreSQL without Docker -->
<dependency>
    <groupId>io.zonky.test</groupId>
    <artifactId>embedded-database-spring-test</artifactId>
    <version>2.5.1</version>
    <scope>test</scope>
</dependency>
<!-- OS-specific binary (change to linux-amd64 or darwin-amd64 for CI) -->
<dependency>
    <groupId>io.zonky.test.postgres</groupId>
    <artifactId>embedded-postgres-binaries-windows-amd64</artifactId>
    <version>16.2.0</version>
    <scope>test</scope>
</dependency>

<!-- WireMock for external HTTP stubs -->
<dependency>
    <groupId>org.wiremock</groupId>
    <artifactId>wiremock</artifactId>
    <version>3.9.1</version>
    <scope>test</scope>
</dependency>

<!-- JMS 3.0 in-process broker (replaces IBM MQ in tests) -->
<dependency>
    <groupId>org.apache.activemq</groupId>
    <artifactId>artemis-jakarta-server</artifactId>
    <scope>test</scope>
</dependency>

<!-- Async assertion without Thread.sleep -->
<dependency>
    <groupId>org.awaitility</groupId>
    <artifactId>awaitility</artifactId>
    <scope>test</scope>
</dependency>

<!-- JWT generation for Keycloak simulation -->
<dependency>
    <groupId>com.nimbusds</groupId>
    <artifactId>nimbus-jose-jwt</artifactId>
    <scope>test</scope>
</dependency>
```

---

## About the `navr` Library

The `navr` library used internally is wrapped inside `PricingServiceAdapter` — it never crosses the hexagonal boundary into the domain or application layers. The `PricingServicePort` interface is what the application service depends on; the adapter translates between that contract and whatever HTTP client (navr, RestClient, Feign) the infrastructure layer uses. Swapping the library requires changing only the adapter class.

---

## CI Considerations

| Environment variable | Purpose |
|---|---|
| `DB_HOST`, `DB_PORT`, `DB_NAME` | Production PostgreSQL (tests use embedded) |
| `MQ_*` | IBM MQ connection (tests use embedded Artemis) |
| `KEYCLOAK_ISSUER_URI` | Production Keycloak (tests use HMAC decoder) |
| `PRICING_SERVICE_URL` | Pricing service base URL (tests use WireMock) |

In CI, **no additional services need to be started** to run the integration test suite. The embedded PostgreSQL binary must match the CI OS architecture — add the appropriate `embedded-postgres-binaries-linux-amd64` artifact for Linux CI runners.
