package com.example.ordermanagement.integration.persistence;

import com.example.ordermanagement.domain.model.Order;
import com.example.ordermanagement.domain.model.OrderItem;
import com.example.ordermanagement.domain.model.OrderStatus;
import com.example.ordermanagement.infrastructure.adapter.out.persistence.OrderPersistenceAdapter;
import io.zonky.test.db.AutoConfigureEmbeddedDatabase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Persistence slice test: @DataJpaTest loads only JPA/DB beans.
 * @AutoConfigureEmbeddedDatabase provides a real PostgreSQL instance
 * — same dialect as production, without Docker or Testcontainers.
 *
 * Each test runs in a transaction that rolls back automatically.
 */
@DataJpaTest
@AutoConfigureEmbeddedDatabase(replace = AutoConfigureEmbeddedDatabase.Replace.ANY)
@ActiveProfiles("test")
@Import(OrderPersistenceAdapter.class)
class OrderPersistenceIT {

    @Autowired
    private OrderPersistenceAdapter adapter;

    @Test
    void save_newOrder_persistsAndReturnsWithId() {
        Order order = buildOrder("cust-001");

        Order saved = adapter.save(order);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCustomerId()).isEqualTo("cust-001");
        assertThat(saved.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(saved.getItems()).hasSize(2);
        assertThat(saved.getTotalPrice()).isEqualByComparingTo("59.97");
    }

    @Test
    void findById_existingOrder_returnsOrder() {
        Order saved = adapter.save(buildOrder("cust-002"));

        Optional<Order> found = adapter.findById(saved.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getCustomerId()).isEqualTo("cust-002");
        assertThat(found.get().getItems()).hasSize(2);
    }

    @Test
    void findById_nonExistentId_returnsEmpty() {
        Optional<Order> found = adapter.findById(UUID.randomUUID());

        assertThat(found).isEmpty();
    }

    @Test
    void findByCustomerId_multipleOrders_returnsAllForCustomer() {
        adapter.save(buildOrder("cust-003"));
        adapter.save(buildOrder("cust-003"));
        adapter.save(buildOrder("cust-999")); // different customer, must not appear

        List<Order> orders = adapter.findByCustomerId("cust-003");

        assertThat(orders).hasSize(2);
        assertThat(orders).allMatch(o -> o.getCustomerId().equals("cust-003"));
    }

    @Test
    void save_updatedStatus_persistsStatusChange() {
        Order order = buildOrder("cust-004");
        Order saved = adapter.save(order);

        saved.confirm();
        Order updated = adapter.save(saved);

        Optional<Order> reloaded = adapter.findById(updated.getId());
        assertThat(reloaded).isPresent();
        assertThat(reloaded.get().getStatus()).isEqualTo(OrderStatus.CONFIRMED);
    }

    @Test
    void save_orderWithMultipleItems_persistsAllItems() {
        Order order = Order.create("cust-005", List.of(
                new OrderItem("P1", "Widget",  3, new BigDecimal("9.99")),
                new OrderItem("P2", "Gadget",  1, new BigDecimal("49.99")),
                new OrderItem("P3", "Doohickey", 5, new BigDecimal("2.50"))
        ));
        order.applyPricing(new BigDecimal("99.94"));

        Order saved = adapter.save(order);

        assertThat(saved.getItems()).hasSize(3);
        assertThat(saved.getItems())
                .extracting(OrderItem::productId)
                .containsExactlyInAnyOrder("P1", "P2", "P3");
    }

    private Order buildOrder(String customerId) {
        Order order = Order.create(customerId, List.of(
                new OrderItem("PROD-1", "Widget A", 2, new BigDecimal("14.99")),
                new OrderItem("PROD-2", "Gadget B", 1, new BigDecimal("29.99"))
        ));
        order.applyPricing(new BigDecimal("59.97"));
        return order;
    }
}
