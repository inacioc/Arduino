package com.example.ordermanagement.adapter.out.persistence;

import com.example.ordermanagement.domain.model.Order;
import com.example.ordermanagement.domain.model.OrderItem;
import com.example.ordermanagement.domain.model.OrderStatus;
import com.example.ordermanagement.domain.port.out.OrderRepositoryPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for OrderPersistenceAdapter against a real PostgreSQL database.
 *
 * Strategy:
 *  - @Transactional + @Rollback: each test runs in a transaction that rolls back
 *    automatically, keeping the DB clean without explicit DELETE scripts.
 *  - For read-heavy tests that pre-load data, @Sql is used alongside @Transactional.
 *
 * Note: @Transactional rollback does NOT work for tests that verify JMS/async
 * side effects. Those tests live in dedicated adapter test classes.
 */
@SpringBootTest(classes = PersistenceTestApplication.class)
@Transactional
@Sql(scripts = "/sql/clean-orders.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class OrderPersistenceAdapterIT {

    @Autowired
    private OrderRepositoryPort orderRepository;

    private static final UUID P_1 = UUID.fromString("33333333-0000-0000-0000-000000000001");
    private static final UUID P_2 = UUID.fromString("33333333-0000-0000-0000-000000000002");

    // ── Save ──────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("save() persists a new order and returns it with same ID")
    void save_persistsNewOrder() {
        Order order = buildOrder("customer-42");

        Order saved = orderRepository.save(order);

        assertThat(saved.getId()).isEqualTo(order.getId());
        assertThat(saved.getCustomerId()).isEqualTo("customer-42");
        assertThat(saved.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(saved.getItems()).hasSize(2);
        assertThat(saved.getTotalAmount()).isEqualByComparingTo("149.96");
    }

    @Test
    @DisplayName("save() persists order items with correct subtotals")
    void save_persistsOrderItemsCorrectly() {
        Order order = buildOrder("customer-items");
        Order saved = orderRepository.save(order);

        assertThat(saved.getItems())
                .extracting(OrderItem::getProductId)
                .containsExactlyInAnyOrder(P_1, P_2);
    }

    @Test
    @DisplayName("save() updates an existing order (status change persists)")
    void save_updatesExistingOrder() {
        Order order = orderRepository.save(buildOrder("customer-update"));

        order.confirm();
        Order updated = orderRepository.save(order);

        assertThat(updated.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        // Verify persisted, not just in-memory
        Order fromDb = orderRepository.findById(updated.getId()).orElseThrow();
        assertThat(fromDb.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
    }

    // ── FindById ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("findById() returns empty Optional for non-existent ID")
    void findById_notFound_returnsEmpty() {
        Optional<Order> result = orderRepository.findById(UUID.randomUUID());

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("findById() returns the order when it exists")
    void findById_found_returnsOrder() {
        Order saved = orderRepository.save(buildOrder("customer-find"));

        Optional<Order> result = orderRepository.findById(saved.getId());

        assertThat(result).isPresent();
        assertThat(result.get().getCustomerId()).isEqualTo("customer-find");
    }

    // ── FindByStatus ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("findByStatus() returns only orders with matching status")
    void findByStatus_returnsMatchingOrders() {
        Order pending   = orderRepository.save(buildOrder("cust-a"));
        Order confirmed = orderRepository.save(buildOrder("cust-b"));
        confirmed.confirm();
        orderRepository.save(confirmed);

        List<Order> pendingOrders = orderRepository.findByStatus(OrderStatus.PENDING);
        List<Order> confirmedOrders = orderRepository.findByStatus(OrderStatus.CONFIRMED);

        assertThat(pendingOrders).extracting(Order::getId).contains(pending.getId());
        assertThat(pendingOrders).extracting(Order::getId).doesNotContain(confirmed.getId());
        assertThat(confirmedOrders).extracting(Order::getId).contains(confirmed.getId());
    }

    // ── FindByCustomerId ──────────────────────────────────────────────────────

    @Test
    @DisplayName("findByCustomerId() returns all orders for the customer")
    void findByCustomerId_returnsAllCustomerOrders() {
        orderRepository.save(buildOrder("target-customer"));
        orderRepository.save(buildOrder("target-customer"));
        orderRepository.save(buildOrder("other-customer"));

        List<Order> orders = orderRepository.findByCustomerId("target-customer");

        assertThat(orders).hasSize(2);
        assertThat(orders).allMatch(o -> o.getCustomerId().equals("target-customer"));
    }

    @Test
    @DisplayName("findByCustomerId() returns empty list for unknown customer")
    void findByCustomerId_unknownCustomer_returnsEmpty() {
        List<Order> orders = orderRepository.findByCustomerId("no-such-customer");

        assertThat(orders).isEmpty();
    }

    // ── DeleteById ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("deleteById() removes the order and its items")
    void deleteById_removesOrderAndItems() {
        Order saved = orderRepository.save(buildOrder("customer-delete"));
        UUID id = saved.getId();

        orderRepository.deleteById(id);

        assertThat(orderRepository.findById(id)).isEmpty();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Order buildOrder(String customerId) {
        // 3 * 29.99 = 89.97 + 1 * 59.99 = 59.99 → total = 149.96
        return Order.create(customerId, List.of(
                new OrderItem(P_1, "Product One", 3, new BigDecimal("29.99")),
                new OrderItem(P_2, "Product Two", 1, new BigDecimal("59.99"))
        ));
    }
}
