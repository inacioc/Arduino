package com.example.ordermanagement.domain.service;

import com.example.ordermanagement.domain.model.Order;
import com.example.ordermanagement.domain.model.OrderStatus;
import com.example.ordermanagement.domain.model.Product;
import com.example.ordermanagement.domain.port.in.CreateOrderUseCase.CreateOrderCommand;
import com.example.ordermanagement.domain.port.in.CreateOrderUseCase.OrderItemCommand;
import com.example.ordermanagement.domain.port.out.OrderEventPort;
import com.example.ordermanagement.domain.port.out.OrderRepositoryPort;
import com.example.ordermanagement.domain.port.out.ProductRepositoryPort;
import com.example.ordermanagement.domain.service.OrderDomainService.OrderItemErrorCode;
import com.example.ordermanagement.domain.service.OrderDomainService.OrderValidationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

/**
 * Pure unit tests for {@link OrderDomainService#createOrder} error accumulation
 * (Notification pattern) — hand-rolled port fakes, no Spring, no database.
 */
class OrderDomainServiceTest {

    private final InMemoryOrderRepository orderRepository = new InMemoryOrderRepository();
    private final InMemoryProductRepository productRepository = new InMemoryProductRepository();
    private final CountingEventPort events = new CountingEventPort();
    private final OrderDomainService service =
            new OrderDomainService(orderRepository, productRepository, events);

    private static final UUID KNOWN       = UUID.fromString("11111111-0000-0000-0000-000000000001");
    private static final UUID UNAVAILABLE = UUID.fromString("11111111-0000-0000-0000-000000000002");
    private static final UUID UNKNOWN     = UUID.fromString("11111111-0000-0000-0000-0000000000ff");

    OrderDomainServiceTest() {
        productRepository.save(Product.create(KNOWN, "Widget", new BigDecimal("49.99"), true));
        productRepository.save(Product.create(UNAVAILABLE, "Out of stock", new BigDecimal("10.00"), false));
    }

    @Test
    @DisplayName("all lines valid → order is created, saved and an event is published")
    void createOrder_allValid_succeeds() {
        Order order = service.createOrder(new CreateOrderCommand("cust-1",
                List.of(new OrderItemCommand(KNOWN, 2, new BigDecimal("49.99")))));

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(orderRepository.count()).isEqualTo(1);
        assertThat(events.created).isEqualTo(1);
    }

    @Test
    @DisplayName("multiple bad lines → ALL errors are collected in one exception, nothing persisted")
    void createOrder_multipleInvalid_collectsAllErrors() {
        OrderValidationException ex = catchThrowableOfType(
                () -> service.createOrder(new CreateOrderCommand("cust-1", List.of(
                        new OrderItemCommand(UNKNOWN, 1, new BigDecimal("10.00")),      // not found
                        new OrderItemCommand(UNAVAILABLE, 2, new BigDecimal("10.00")),  // not available
                        new OrderItemCommand(KNOWN, 1, new BigDecimal("49.99"))))),     // valid
                OrderValidationException.class);

        assertThat(ex).isNotNull();
        assertThat(ex.getErrors()).hasSize(2);
        assertThat(ex.getErrors()).extracting(OrderDomainService.OrderItemError::code)
                .containsExactlyInAnyOrder(
                        OrderItemErrorCode.PRODUCT_NOT_FOUND,
                        OrderItemErrorCode.PRODUCT_NOT_AVAILABLE);
        // Nothing was persisted and no event fired
        assertThat(orderRepository.count()).isZero();
        assertThat(events.created).isZero();
    }

    @Test
    @DisplayName("one bad line among valid ones still fails the whole order")
    void createOrder_oneInvalid_failsAtomically() {
        OrderValidationException ex = catchThrowableOfType(
                () -> service.createOrder(new CreateOrderCommand("cust-1", List.of(
                        new OrderItemCommand(KNOWN, 1, new BigDecimal("49.99")),
                        new OrderItemCommand(UNKNOWN, 1, new BigDecimal("10.00"))))),
                OrderValidationException.class);

        assertThat(ex.getErrors()).extracting(OrderDomainService.OrderItemError::code)
                .containsExactly(OrderItemErrorCode.PRODUCT_NOT_FOUND);
        assertThat(orderRepository.count()).isZero();
    }

    // ── Fakes ───────────────────────────────────────────────────────────────

    private static final class InMemoryOrderRepository implements OrderRepositoryPort {
        private final Map<UUID, Order> store = new HashMap<>();

        int count() { return store.size(); }

        @Override public Order save(Order order) { store.put(order.getId(), order); return order; }
        @Override public Optional<Order> findById(UUID id) { return Optional.ofNullable(store.get(id)); }
        @Override public List<Order> findByStatus(OrderStatus status) {
            return store.values().stream().filter(o -> o.getStatus() == status).toList();
        }
        @Override public List<Order> findByCustomerId(String customerId) {
            return store.values().stream().filter(o -> o.getCustomerId().equals(customerId)).toList();
        }
        @Override public void deleteById(UUID id) { store.remove(id); }
    }

    private static final class InMemoryProductRepository implements ProductRepositoryPort {
        private final Map<UUID, Product> store = new HashMap<>();

        @Override public Product save(Product product) { store.put(product.getId(), product); return product; }
        @Override public Optional<Product> findById(UUID id) { return Optional.ofNullable(store.get(id)); }
        @Override public List<Product> findAll() { return List.copyOf(store.values()); }
        @Override public void deleteById(UUID id) { store.remove(id); }
    }

    private static final class CountingEventPort implements OrderEventPort {
        int created;
        int completed;

        @Override public void publishOrderCreated(Order order) { created++; }
        @Override public void publishOrderCompleted(Order order) { completed++; }
    }
}
