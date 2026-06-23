package com.example.ordermanagement.domain.service;

import com.example.ordermanagement.domain.model.Order;
import com.example.ordermanagement.domain.model.OrderItem;
import com.example.ordermanagement.domain.model.OrderStatus;
import com.example.ordermanagement.domain.model.Product;
import com.example.ordermanagement.domain.port.in.CreateOrderUseCase;
import com.example.ordermanagement.domain.port.in.GetOrderUseCase;
import com.example.ordermanagement.domain.port.in.OrderResult;
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
    public OrderResult createOrder(CreateOrderCommand command) {
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
        return OrderResult.from(saved);
    }

    // ── GetOrderUseCase ───────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public Optional<OrderResult> findById(UUID orderId) {
        return orderRepository.findById(orderId).map(OrderResult::from);
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrderResult> findByStatus(String status) {
        OrderStatus parsed = OrderStatus.valueOf(status.trim().toUpperCase());
        return orderRepository.findByStatus(parsed).stream()
                .map(OrderResult::from)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrderResult> findByCustomerId(String customerId) {
        return orderRepository.findByCustomerId(customerId).stream()
                .map(OrderResult::from)
                .toList();
    }

    // ── ProcessOrderUseCase ───────────────────────────────────────────────────

    @Override
    public OrderResult confirmOrder(UUID orderId) {
        Order order = findOrThrow(orderId);
        order.confirm();
        return OrderResult.from(orderRepository.save(order));
    }

    @Override
    public OrderResult completeOrder(UUID orderId) {
        Order order = findOrThrow(orderId);
        order.startProcessing();
        order.complete();
        Order saved = orderRepository.save(order);
        orderEvents.publishOrderCompleted(saved);
        return OrderResult.from(saved);
    }

    @Override
    public OrderResult cancelOrder(UUID orderId) {
        Order order = findOrThrow(orderId);
        order.cancel();
        return OrderResult.from(orderRepository.save(order));
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
