package com.example.ordermanagement.domain.service;

import com.example.ordermanagement.domain.model.Order;
import com.example.ordermanagement.domain.model.OrderStatus;
import com.example.ordermanagement.domain.port.in.ChangeOrderStatusUseCase;
import com.example.ordermanagement.domain.port.out.OrderRepositoryPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * Implements {@link ChangeOrderStatusUseCase}.
 * <p>
 * Kept separate from {@link OrderDomainService} on purpose: it needs only the
 * {@link OrderRepositoryPort}, so a driving adapter (e.g. the batch runner) can advance
 * order statuses through this inbound port without dragging in the product/event ports
 * that {@code OrderDomainService} requires. The load → advance → save orchestration lives
 * here, inside the hexagon, rather than in the adapter.
 */
@Service
@Transactional
public class ChangeOrderStatusService implements ChangeOrderStatusUseCase {

    private final OrderRepositoryPort orderRepository;

    public ChangeOrderStatusService(OrderRepositoryPort orderRepository) {
        this.orderRepository = orderRepository;
    }

    @Override
    public ChangeOrderStatusResult changeStatus(UUID orderId, String targetStatus) {
        OrderStatus target;
        try {
            target = OrderStatus.valueOf(targetStatus.trim().toUpperCase());
        } catch (IllegalArgumentException | NullPointerException e) {
            return new ChangeOrderStatusResult(orderId, null, Outcome.INVALID_TARGET_STATUS);
        }

        Optional<Order> found = orderRepository.findById(orderId);
        if (found.isEmpty()) {
            return new ChangeOrderStatusResult(orderId, null, Outcome.ORDER_NOT_FOUND);
        }

        Order order = found.get();
        try {
            order.advanceTo(target);
        } catch (IllegalStateException e) {
            // Illegal transition: report the unchanged current status, don't persist.
            return new ChangeOrderStatusResult(
                    orderId, order.getStatus().name(), Outcome.INVALID_TRANSITION);
        }

        Order saved = orderRepository.save(order);
        return new ChangeOrderStatusResult(orderId, saved.getStatus().name(), Outcome.CHANGED);
    }
}
