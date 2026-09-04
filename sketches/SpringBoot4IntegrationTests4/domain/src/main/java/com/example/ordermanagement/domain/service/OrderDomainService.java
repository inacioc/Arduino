package com.example.ordermanagement.domain.service;

import com.example.ordermanagement.domain.event.OrderCreatedIntegrationEvent;
import com.example.ordermanagement.domain.model.Order;
import com.example.ordermanagement.domain.model.OrderItem;
import com.example.ordermanagement.domain.model.OrderStatus;
import com.example.ordermanagement.domain.model.Product;
import com.example.ordermanagement.domain.port.in.CreateOrderUseCase;
import com.example.ordermanagement.domain.port.in.GetOrderUseCase;
import com.example.ordermanagement.domain.port.in.ProcessOrderUseCase;
import com.example.ordermanagement.domain.port.out.OrderEventPort;
import com.example.ordermanagement.domain.port.out.OrderRepositoryPort;
import com.example.ordermanagement.domain.port.out.ProductRepositoryPort;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@Transactional
public class OrderDomainService implements CreateOrderUseCase, GetOrderUseCase, ProcessOrderUseCase {

    private final OrderRepositoryPort orderRepository;
    private final ProductRepositoryPort productRepository;
    private final OrderEventPort orderEvents;
    private final ApplicationEventPublisher eventPublisher;

    public OrderDomainService(OrderRepositoryPort orderRepository,
                               ProductRepositoryPort productRepository,
                               OrderEventPort orderEvents,
                               ApplicationEventPublisher eventPublisher) {
        this.orderRepository   = orderRepository;
        this.productRepository = productRepository;
        this.orderEvents       = orderEvents;
        this.eventPublisher    = eventPublisher;
    }

    // ── CreateOrderUseCase ────────────────────────────────────────────────────

    @Override
    public Order createOrder(CreateOrderCommand command) {
        // Notification pattern: validate every line, collecting problems as we go, so
        // the caller gets ALL bad lines at once instead of failing on the first one.
        List<OrderItem> items = new ArrayList<>();
        List<OrderItemError> errors = new ArrayList<>();

        for (var itemCmd : command.items()) {
            Optional<Product> found = productRepository.findById(itemCmd.productId());
            if (found.isEmpty()) {
                errors.add(new OrderItemError(itemCmd.productId(),
                        OrderItemErrorCode.PRODUCT_NOT_FOUND,
                        "Product not found: " + itemCmd.productId()));
                continue;
            }

            Product product = found.get();
            if (!product.isOrderable()) {
                errors.add(new OrderItemError(itemCmd.productId(),
                        OrderItemErrorCode.PRODUCT_NOT_AVAILABLE,
                        "Product not available: " + itemCmd.productId()));
                continue;
            }

            items.add(new OrderItem(
                    product.getId(),
                    product.getName(),
                    itemCmd.quantity(),
                    itemCmd.unitPrice()
            ));
        }

        // One aggregate failure carrying every collected error — nothing is persisted.
        if (!errors.isEmpty()) {
            throw new OrderValidationException(errors);
        }

        Order order = Order.create(command.customerId(), items);
        Order saved = orderRepository.save(order);

        orderEvents.publishOrderCreated(saved);
        eventPublisher.publishEvent(new OrderCreatedIntegrationEvent(
                saved.getId(), saved.getCustomerId(), LocalDateTime.now()));
        return saved;
    }

    // ── GetOrderUseCase ───────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public Optional<Order> findById(UUID orderId) {
        return orderRepository.findById(orderId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Order> findByStatus(OrderStatus status) {
        return orderRepository.findByStatus(status);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Order> findByCustomerId(String customerId) {
        return orderRepository.findByCustomerId(customerId);
    }

    // ── ProcessOrderUseCase ───────────────────────────────────────────────────

    @Override
    public Order confirmOrder(UUID orderId) {
        Order order = findOrThrow(orderId);
        order.confirm();
        return orderRepository.save(order);
    }

    @Override
    public Order completeOrder(UUID orderId) {
        Order order = findOrThrow(orderId);
        order.startProcessing();
        order.complete();
        Order saved = orderRepository.save(order);
        orderEvents.publishOrderCompleted(saved);
        return saved;
    }

    @Override
    public Order cancelOrder(UUID orderId) {
        Order order = findOrThrow(orderId);
        order.cancel();
        return orderRepository.save(order);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Order findOrThrow(UUID orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));
    }

    // ── Domain Exceptions ─────────────────────────────────────────────────────

    public static class OrderNotFoundException extends RuntimeException {
        public OrderNotFoundException(UUID id) {
            super("Order not found: " + id);
        }
    }

    /** Machine-readable code for a single invalid order line. Extend as new rules appear. */
    public enum OrderItemErrorCode {
        PRODUCT_NOT_FOUND,
        PRODUCT_NOT_AVAILABLE
    }

    /** One collected validation error, tied to the offending product line. */
    public record OrderItemError(UUID productId, OrderItemErrorCode code, String message) {}

    /**
     * Aggregate of every validation error found while building an order.
     * <p>
     * Thrown once, at the end of validation, so the caller sees all bad lines at once
     * (Notification pattern) instead of discovering them one request at a time.
     */
    public static class OrderValidationException extends RuntimeException {
        private final transient List<OrderItemError> errors;

        public OrderValidationException(List<OrderItemError> errors) {
            super("Order validation failed with " + errors.size() + " error(s)");
            this.errors = List.copyOf(errors);
        }

        public List<OrderItemError> getErrors() {
            return errors;
        }
    }
}
