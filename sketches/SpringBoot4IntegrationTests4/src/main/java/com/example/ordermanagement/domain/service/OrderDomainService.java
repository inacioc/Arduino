package com.example.ordermanagement.domain.service;

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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@Transactional
public class OrderDomainService implements CreateOrderUseCase, GetOrderUseCase, ProcessOrderUseCase {

    private final OrderRepositoryPort orderRepository;
    private final ProductRepositoryPort productRepository;
    private final OrderEventPort orderEvents;

    public OrderDomainService(OrderRepositoryPort orderRepository,
                               ProductRepositoryPort productRepository,
                               OrderEventPort orderEvents) {
        this.orderRepository   = orderRepository;
        this.productRepository = productRepository;
        this.orderEvents       = orderEvents;
    }

    // ── CreateOrderUseCase ────────────────────────────────────────────────────

    @Override
    public Order createOrder(CreateOrderCommand command) {
        List<OrderItem> items = command.items().stream()
                .map(itemCmd -> {
                    // Validate product exists and is available
                    Product product = productRepository
                            .findById(itemCmd.productId())
                            .orElseThrow(() -> new ProductNotFoundException(itemCmd.productId()));

                    if (!product.isOrderable()) {
                        throw new ProductNotAvailableException(itemCmd.productId());
                    }

                    return new OrderItem(
                            product.getId(),
                            product.getName(),
                            itemCmd.quantity(),
                            itemCmd.unitPrice()
                    );
                })
                .toList();

        Order order = Order.create(command.customerId(), items);
        Order saved = orderRepository.save(order);

        orderEvents.publishOrderCreated(saved);
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

    public static class ProductNotFoundException extends RuntimeException {
        public ProductNotFoundException(String productId) {
            super("Product not found: " + productId);
        }
    }

    public static class ProductNotAvailableException extends RuntimeException {
        public ProductNotAvailableException(String productId) {
            super("Product not available: " + productId);
        }
    }
}
