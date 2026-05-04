package com.example.orders.infrastructure.out.persistence;

import com.example.orders.domain.model.Money;
import com.example.orders.domain.model.Order;
import com.example.orders.domain.model.OrderItem;
import com.example.orders.domain.model.OrderStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Persistence slice test: loads only the JPA layer (entities, repositories, Flyway).
 *
 * Uses @ServiceConnection to automatically wire the PostgreSQL Testcontainer into
 * Spring's DataSource — no manual @DynamicPropertySource needed.
 *
 * @DataJpaTest does NOT load the full context, so it runs much faster than @SpringBootTest.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE) // keep Postgres, don't swap in H2
@Testcontainers
@ActiveProfiles("test")
@Import(OrderPersistenceAdapter.class) // import the adapter under test explicitly
class OrderPersistenceAdapterIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:17-alpine")
                    .withDatabaseName("orders_slice_test")
                    .withUsername("test")
                    .withPassword("test");

    @Autowired
    private OrderPersistenceAdapter adapter;

    // ── save ────────────────────────────────────────────────────────────────

    @Test
    void shouldPersistOrderAndReturnItWithGeneratedId() {
        Order order = Order.create("cust-1", List.of(
                new OrderItem("prod-A", "Widget A", 2, Money.of(15.00, "USD"))
        ));

        Order saved = adapter.save(order);

        assertThat(saved.getId()).isNotBlank();
        assertThat(saved.getCustomerId()).isEqualTo("cust-1");
        assertThat(saved.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(saved.getItems()).hasSize(1);
        assertThat(saved.total().amount()).isEqualByComparingTo("30.00");
        assertThat(saved.total().currency()).isEqualTo("USD");
    }

    @Test
    void shouldPersistOrderWithMultipleItems() {
        Order order = Order.create("cust-2", List.of(
                new OrderItem("prod-B", "Widget B", 1, Money.of(10.00, "USD")),
                new OrderItem("prod-C", "Widget C", 3, Money.of(5.00, "USD"))
        ));

        Order saved = adapter.save(order);

        assertThat(saved.getItems()).hasSize(2);
        assertThat(saved.total().amount()).isEqualByComparingTo("25.00");
    }

    // ── findById ────────────────────────────────────────────────────────────

    @Test
    void shouldFindOrderByIdAfterSaving() {
        Order order = Order.create("cust-3", List.of(
                new OrderItem("prod-D", "Widget D", 1, Money.of(99.99, "USD"))
        ));
        Order saved = adapter.save(order);

        Optional<Order> found = adapter.findById(saved.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(saved.getId());
        assertThat(found.get().getCustomerId()).isEqualTo("cust-3");
        assertThat(found.get().getItems()).hasSize(1);
        assertThat(found.get().getItems().getFirst().productId()).isEqualTo("prod-D");
    }

    @Test
    void shouldReturnEmptyOptionalForNonExistentId() {
        Optional<Order> found = adapter.findById("does-not-exist");

        assertThat(found).isEmpty();
    }

    // ── domain state transitions are persisted correctly ────────────────────

    @Test
    void shouldReflectStatusChangeAfterResave() {
        Order order = Order.create("cust-4", List.of(
                new OrderItem("prod-E", "Widget E", 1, Money.of(50.00, "USD"))
        ));
        Order saved = adapter.save(order);

        // Simulate business state change and re-save
        saved.confirm();
        adapter.save(saved);

        Order reloaded = adapter.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
    }
}
