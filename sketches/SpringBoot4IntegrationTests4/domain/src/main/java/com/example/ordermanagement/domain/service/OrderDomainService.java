package com.example.ordermanagement.domain.service;

import com.example.ordermanagement.domain.event.OrderCreatedIntegrationEvent;
import com.example.ordermanagement.domain.exception.OrderNotFoundException;
import com.example.ordermanagement.domain.exception.ProductNotFoundException;
import com.example.ordermanagement.domain.model.Order;
import com.example.ordermanagement.domain.model.OrderItem;
import com.example.ordermanagement.domain.model.OrderStatus;
import com.example.ordermanagement.domain.model.Product;
import com.example.ordermanagement.domain.port.in.CreateOrderUseCase;
import com.example.ordermanagement.domain.port.in.GetOrderUseCase;
import com.example.ordermanagement.domain.port.in.ProcessOrderUseCase;
import com.example.ordermanagement.domain.port.out.OrderEventPort;
import com.example.ordermanagement.domain.port.out.OrderIntegrationEventPort;
import com.example.ordermanagement.domain.port.out.OrderRepositoryPort;
import com.example.ordermanagement.domain.port.out.ProductRepositoryPort;
import com.example.ordermanagement.domain.validation.CreateOrderValidator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@Transactional
public class OrderDomainService implements CreateOrderUseCase, GetOrderUseCase, ProcessOrderUseCase {

    private final OrderRepositoryPort orderRepository;
    private final ProductRepositoryPort productRepository;
    private final OrderEventPort orderEvents;
    private final OrderIntegrationEventPort integrationEvents;
    private final CreateOrderValidator createOrderValidator;

    public OrderDomainService(OrderRepositoryPort orderRepository,
                               ProductRepositoryPort productRepository,
                               OrderEventPort orderEvents,
                               OrderIntegrationEventPort integrationEvents,
                               CreateOrderValidator createOrderValidator) {
        this.orderRepository   = orderRepository;
        this.productRepository = productRepository;
        this.orderEvents       = orderEvents;
        this.integrationEvents = integrationEvents;
        this.createOrderValidator = createOrderValidator;
    }

    // ── CreateOrderUseCase ────────────────────────────────────────────────────

    @Override
    public Order createOrder(CreateOrderCommand command) {
        createOrderValidator.assertValid(command);

        List<OrderItem> items = command.items().stream().map(this::toOrderItem).toList();
        Order order = Order.create(command.customerId(), items);
        Order saved = orderRepository.save(order);

        orderEvents.publishOrderCreated(saved);
        integrationEvents.publish(new OrderCreatedIntegrationEvent(
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

    private OrderItem toOrderItem(OrderItemCommand line) {
        // Already found by the validator moments ago, in this same transaction.
        Product product = productRepository.findById(line.productId())
                .orElseThrow(() -> new ProductNotFoundException(line.productId().toString()));
        return new OrderItem(product.getId(), product.getName(), line.quantity(), line.unitPrice());
    }

    private Order findOrThrow(UUID orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId.toString()));
    }
}
