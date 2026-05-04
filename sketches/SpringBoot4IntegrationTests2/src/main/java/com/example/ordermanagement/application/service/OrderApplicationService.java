package com.example.ordermanagement.application.service;

import com.example.ordermanagement.domain.model.Order;
import com.example.ordermanagement.domain.model.OrderItem;
import com.example.ordermanagement.domain.port.in.CreateOrderUseCase;
import com.example.ordermanagement.domain.port.in.GetOrderUseCase;
import com.example.ordermanagement.domain.port.in.UpdateOrderStatusUseCase;
import com.example.ordermanagement.domain.port.out.NotificationPort;
import com.example.ordermanagement.domain.port.out.OrderRepositoryPort;
import com.example.ordermanagement.domain.port.out.PricingServicePort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@Transactional
public class OrderApplicationService implements CreateOrderUseCase, GetOrderUseCase, UpdateOrderStatusUseCase {

    private final OrderRepositoryPort orderRepository;
    private final NotificationPort notificationPort;
    private final PricingServicePort pricingService;

    public OrderApplicationService(OrderRepositoryPort orderRepository,
                                   NotificationPort notificationPort,
                                   PricingServicePort pricingService) {
        this.orderRepository = orderRepository;
        this.notificationPort = notificationPort;
        this.pricingService = pricingService;
    }

    @Override
    public Order createOrder(CreateOrderCommand command) {
        List<OrderItem> items = command.items().stream()
                .map(dto -> new OrderItem(dto.productId(), dto.productName(), dto.quantity(), BigDecimal.TEN))
                .toList();

        Order order = Order.create(command.customerId(), items);

        BigDecimal totalPrice = pricingService.calculateTotalPrice(order);
        order.applyPricing(totalPrice);

        Order saved = orderRepository.save(order);
        notificationPort.sendOrderCreatedNotification(saved);

        return saved;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Order> getOrderById(UUID orderId) {
        return orderRepository.findById(orderId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Order> getOrdersByCustomer(String customerId) {
        return orderRepository.findByCustomerId(customerId);
    }

    @Override
    public Order confirmOrder(UUID orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));
        order.confirm();
        Order saved = orderRepository.save(order);
        notificationPort.sendOrderStatusChangedNotification(saved);
        return saved;
    }

    @Override
    public Order cancelOrder(UUID orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));
        order.cancel();
        Order saved = orderRepository.save(order);
        notificationPort.sendOrderStatusChangedNotification(saved);
        return saved;
    }
}
