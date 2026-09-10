package com.example.ordermanagement.domain.service;

import com.example.ordermanagement.domain.event.OrderCreatedIntegrationEvent;
import com.example.ordermanagement.domain.exception.OrderNotFoundException;
import com.example.ordermanagement.domain.exception.OrderValidationException;
import com.example.ordermanagement.domain.exception.OrderValidationException.OrderItemError;
import com.example.ordermanagement.domain.exception.OrderValidationException.OrderItemErrorCode;
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

            // Client-submitted price must match the catalogue price at order time —
            // catches stale prices shown by a client and rejects tampering, without
            // trusting the request body as the source of truth for money.
            if (product.getPrice().compareTo(itemCmd.unitPrice()) != 0) {
                errors.add(new OrderItemError(itemCmd.productId(),
                        OrderItemErrorCode.PRICE_MISMATCH,
                        "Price mismatch for product %s: submitted %s, current price %s"
                                .formatted(itemCmd.productId(), itemCmd.unitPrice(), product.getPrice())));
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

    // OrderNotFoundException / OrderValidationException (with its OrderItemErrorCode /
    // OrderItemError) used to live here as nested classes; they're now top-level types in
    // domain.exception (imported above) so GlobalExceptionHandler and any other driving
    // adapter can depend on them without depending on this concrete service class. See
    // Validation.md.

    private Order findOrThrow(UUID orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId.toString()));
    }
}
