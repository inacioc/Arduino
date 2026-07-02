package com.example.ordermanagement.domain.service;

import com.example.ordermanagement.domain.model.Order;
import com.example.ordermanagement.domain.model.OrderItem;
import com.example.ordermanagement.domain.model.OrderStatus;
import com.example.ordermanagement.domain.port.in.ChangeOrderStatusUseCase.ChangeOrderStatusResult;
import com.example.ordermanagement.domain.port.in.ChangeOrderStatusUseCase.Outcome;
import com.example.ordermanagement.domain.port.out.OrderRepositoryPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pure unit tests for {@link ChangeOrderStatusService} using an in-memory fake of the
 * {@link OrderRepositoryPort} — no Spring, no database. Mirrors the outcomes the batch
 * job relies on.
 */
class ChangeOrderStatusServiceTest {

    private final InMemoryOrderRepository repository = new InMemoryOrderRepository();
    private final ChangeOrderStatusService service = new ChangeOrderStatusService(repository);

    @Test
    @DisplayName("advances the order through intermediate states and persists it")
    void changeStatus_valid_returnsChangedAndPersists() {
        Order order = repository.save(newOrder(OrderStatus.PENDING));

        ChangeOrderStatusResult result = service.changeStatus(order.getId(), "COMPLETED");

        assertThat(result.outcome()).isEqualTo(Outcome.CHANGED);
        assertThat(result.status()).isEqualTo("COMPLETED");
        assertThat(repository.findById(order.getId()).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.COMPLETED);
    }

    @Test
    @DisplayName("unknown target status -> INVALID_TARGET_STATUS, no status, not persisted")
    void changeStatus_unknownTarget_returnsInvalidTargetStatus() {
        Order order = repository.save(newOrder(OrderStatus.PENDING));

        ChangeOrderStatusResult result = service.changeStatus(order.getId(), "FOO");

        assertThat(result.outcome()).isEqualTo(Outcome.INVALID_TARGET_STATUS);
        assertThat(result.status()).isNull();
        assertThat(repository.findById(order.getId()).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.PENDING);
    }

    @Test
    @DisplayName("missing order -> ORDER_NOT_FOUND with no status")
    void changeStatus_missingOrder_returnsNotFound() {
        ChangeOrderStatusResult result = service.changeStatus(UUID.randomUUID(), "CONFIRMED");

        assertThat(result.outcome()).isEqualTo(Outcome.ORDER_NOT_FOUND);
        assertThat(result.status()).isNull();
    }

    @Test
    @DisplayName("illegal transition -> INVALID_TRANSITION reports the unchanged status")
    void changeStatus_illegalTransition_returnsInvalidTransition() {
        Order order = newOrder(OrderStatus.PENDING);
        order.advanceTo(OrderStatus.COMPLETED);
        repository.save(order);

        ChangeOrderStatusResult result = service.changeStatus(order.getId(), "CONFIRMED");

        assertThat(result.outcome()).isEqualTo(Outcome.INVALID_TRANSITION);
        assertThat(result.status()).isEqualTo("COMPLETED");   // unchanged
        assertThat(repository.findById(order.getId()).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.COMPLETED);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static Order newOrder(OrderStatus target) {
        Order order = Order.create("cust-1", List.of(
                new OrderItem(UUID.randomUUID(), "Test Product", 1, new BigDecimal("10.00"))));
        if (target != OrderStatus.PENDING) {
            order.advanceTo(target);
        }
        return order;
    }

    /** Minimal in-memory {@link OrderRepositoryPort} for unit testing. */
    private static final class InMemoryOrderRepository implements OrderRepositoryPort {
        private final Map<UUID, Order> store = new HashMap<>();

        @Override
        public Order save(Order order) {
            store.put(order.getId(), order);
            return order;
        }

        @Override
        public Optional<Order> findById(UUID id) {
            return Optional.ofNullable(store.get(id));
        }

        @Override
        public List<Order> findByStatus(OrderStatus status) {
            return store.values().stream().filter(o -> o.getStatus() == status).toList();
        }

        @Override
        public List<Order> findByCustomerId(String customerId) {
            return store.values().stream()
                    .filter(o -> o.getCustomerId().equals(customerId)).toList();
        }

        @Override
        public void deleteById(UUID id) {
            store.remove(id);
        }
    }
}
