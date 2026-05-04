package com.example.orders.domain.service;

import com.example.orders.domain.model.Order;
import com.example.orders.domain.model.OrderItem;
import com.example.orders.domain.port.in.CreateOrderCommand;
import com.example.orders.domain.port.in.CreateOrderUseCase;
import com.example.orders.domain.port.in.GetOrderUseCase;
import com.example.orders.domain.port.out.InventoryPort;
import com.example.orders.domain.port.out.OrderEventPublisher;
import com.example.orders.domain.port.out.OrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@Transactional
public class OrderDomainService implements CreateOrderUseCase, GetOrderUseCase {

    private final OrderRepository orderRepository;
    private final InventoryPort inventoryPort;
    private final OrderEventPublisher eventPublisher;

    public OrderDomainService(OrderRepository orderRepository,
                              InventoryPort inventoryPort,
                              OrderEventPublisher eventPublisher) {
        this.orderRepository = orderRepository;
        this.inventoryPort = inventoryPort;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public Order createOrder(CreateOrderCommand command) {
        validateInventory(command);

        Order order = Order.create(command.customerId(), command.items());
        Order saved = orderRepository.save(order);

        eventPublisher.publishOrderCreated(saved);

        return saved;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Order> getOrder(String orderId) {
        return orderRepository.findById(orderId);
    }

    private void validateInventory(CreateOrderCommand command) {
        for (OrderItem item : command.items()) {
            if (!inventoryPort.isAvailable(item.productId(), item.quantity())) {
                throw new InsufficientInventoryException(
                        "Product %s has insufficient inventory for quantity %d"
                                .formatted(item.productId(), item.quantity()));
            }
        }
    }
}
