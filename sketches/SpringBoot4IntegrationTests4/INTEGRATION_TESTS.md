# Integration Tests Guide

## Architecture

Packages are organised as Spring Modulith modules (one per business capability); the
files below are spread over the Maven modules `domain`, `adapter-out-*`, `adapter-events`
and `adapter-in-web`, but share these package roots:

```
src/
├── main/java/com/example/ordermanagement/
│   ├── order/                   # module "order"
│   │   ├── *UseCase, Order*Event, OrderNotFoundException   # public API (root)
│   │   └── internal/
│   │       ├── domain/          # Order, OrderItem, OrderStatus, OrderDomainService, CreateOrderValidator
│   │       ├── port/out/        # Driven ports: OrderRepositoryPort, OrderEventPort, ...
│   │       └── adapter/
│   │           ├── in/web/      # OrderController + DTOs
│   │           ├── out/persistence/  # JPA entities, OrderPersistenceAdapter
│   │           ├── out/messaging/    # OrderMqPublisher, OrderMqListener (IBM MQ)
│   │           ├── out/batch/        # Spring Batch 5 job (CONFIRMED → COMPLETED)
│   │           └── events/           # OrderStatusEventPublisher (Spring Modulith events)
│   ├── customer/                # module "customer" (same shape: Customer aggregate + use cases at the root)
│   ├── product/                 # module "product"  (same shape)
│   ├── notification/            # module "notification": audit + simulated e-mail listeners
│   ├── shared/                  # exceptions hierarchy, DomainValidator, ValidationNotification
│   └── infrastructure/          # config/ (SecurityConfig, JmsConfig), web/ (GlobalExceptionHandler, error/)
└── test/
    ├── support/             # IntegrationTestBase, JwtHelper, TestSecurityConfig
    └── adapter/
        ├── in/web/          # OrderControllerIT, SecurityIT
        └── out/
            ├── persistence/ # OrderPersistenceAdapterIT
            ├── messaging/   # OrderMqPublisherIT
            └── batch/       # OrderBatchJobIT
    OrderFlowIT.java         # Full end-to-end test
```

## Required Infrastructure

| Service     | Default host | Purpose                        | Override env var          |
|-------------|--------------|--------------------------------|---------------------------|
| PostgreSQL  | localhost:5433 | Domain data + Batch metadata | `TEST_DB_URL`, `TEST_DB_USER`, `TEST_DB_PASS` |
| IBM MQ      | localhost:1414 | Order event publishing       | `TEST_MQ_HOST`, `TEST_MQ_PORT`, `TEST_MQ_QUEUE_MANAGER`, etc. |
| Keycloak    | _not needed_   | Replaced by mock JwtDecoder  | — |

### PostgreSQL setup

```sql
CREATE USER orderuser WITH PASSWORD 'orderpass';
CREATE DATABASE orderdb_test OWNER orderuser;
-- Flyway will create tables automatically on first run
```

### IBM MQ setup (DEV image)

The tests expect a queue named `ORDER.EVENTS.TEST.QUEUE` on queue manager `QM1`.

Using IBM MQ Developer image (download from IBM Fix Central or use the community Docker image):
```
Queue Manager : QM1
Channel       : DEV.APP.SVRCONN
Port          : 1414
User          : app
Password      : passw0rd
```

Create the queue:
```
DEFINE QLOCAL('ORDER.EVENTS.TEST.QUEUE') DEFPSIST(YES)
```

## Running the tests

```bash
# Unit tests only (excludes *IT.java)
mvn test

# Integration tests only
mvn failsafe:integration-test

# All tests
mvn verify

# With custom DB
TEST_DB_URL=jdbc:postgresql://myhost:5432/testdb mvn verify
```

## Test Patterns Used

### 1. Controller Tests — `OrderControllerIT`
- `@SpringBootTest` + `@AutoConfigureMockMvc` (full context, real filters)
- `SecurityMockMvcRequestPostProcessors.jwt()` for Keycloak mocking
- `@MockBean ProductRepositoryPort` to stub the product catalogue lookup
- `@Sql` for DB setup/teardown per test

### 2. Repository Tests — `OrderPersistenceAdapterIT`
- `@Transactional` with automatic rollback (no explicit cleanup needed)
- Tests the full JPA → PostgreSQL path

### 3. IBM MQ Tests — `OrderMqPublisherIT`
- Connects to real IBM MQ (no embedded broker)
- `@BeforeEach` drains queue to prevent test interference
- `JmsTemplate.receiveAndConvert()` for synchronous message assertion
- Polling pattern for async listener verification

### 4. Batch Tests — `OrderBatchJobIT`
- `@SpringBatchTest` provides `JobLauncherTestUtils` and `JobRepositoryTestUtils`
- Tests job-level and step-level execution
- Verifies read/write counts in `StepExecution`

### 5. Full Flow — `OrderFlowIT`
- End-to-end: REST → DB → MQ
- Verifies state at each layer after each operation

## Keycloak JWT Mocking

In production, Spring Security validates JWT tokens against Keycloak's JWKS endpoint.
In tests, `TestSecurityConfig` replaces the real `JwtDecoder` with a no-op bean, and
`SecurityMockMvcRequestPostProcessors.jwt()` injects a pre-built `SecurityContext`
directly — no network call needed.

JWT claims match Keycloak's structure exactly:
```java
jwt().jwt(b -> b
    .subject("user-id")
    .claim("realm_access", Map.of("roles", List.of("ADMIN")))
)
.authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))
```
