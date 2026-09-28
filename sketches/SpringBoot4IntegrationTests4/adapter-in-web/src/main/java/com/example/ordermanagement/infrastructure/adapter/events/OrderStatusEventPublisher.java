package com.example.ordermanagement.infrastructure.adapter.events;

import com.example.ordermanagement.domain.event.OrderCancelledEvent;
import com.example.ordermanagement.domain.event.OrderCompletedEvent;
import com.example.ordermanagement.domain.event.OrderConfirmedEvent;
import com.example.ordermanagement.domain.model.Order;
import com.example.ordermanagement.domain.port.out.OrderStatusEventPort;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Thin pass-through adapter: translates each {@link Order} status transition into its
 * corresponding domain event and hands it to Spring's {@link ApplicationEventPublisher}.
 * Everything Modulith-specific about durability, retry and dispatch lives in the listener
 * side ({@link OrderStatusAuditListener}) and the JPA-backed registry, not here.
 */
@Component
public class OrderStatusEventPublisher implements OrderStatusEventPort {

    private final ApplicationEventPublisher eventPublisher;

    public OrderStatusEventPublisher(ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    @Override
    public void confirmed(Order order) {
        eventPublisher.publishEvent(
                new OrderConfirmedEvent(order.getId(), order.getCustomerId(), LocalDateTime.now()));
    }

    @Override
    public void completed(Order order) {
        eventPublisher.publishEvent(
                new OrderCompletedEvent(order.getId(), order.getCustomerId(), LocalDateTime.now()));
    }

    @Override
    public void cancelled(Order order) {
        eventPublisher.publishEvent(
                new OrderCancelledEvent(order.getId(), order.getCustomerId(), LocalDateTime.now()));
    }
}
