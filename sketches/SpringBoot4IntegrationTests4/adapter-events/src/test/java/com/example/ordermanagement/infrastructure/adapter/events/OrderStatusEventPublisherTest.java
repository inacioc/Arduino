package com.example.ordermanagement.infrastructure.adapter.events;

import com.example.ordermanagement.domain.event.OrderCancelledEvent;
import com.example.ordermanagement.domain.event.OrderCompletedEvent;
import com.example.ordermanagement.domain.event.OrderConfirmedEvent;
import com.example.ordermanagement.domain.model.Order;
import com.example.ordermanagement.domain.model.OrderItem;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class OrderStatusEventPublisherTest {

    private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
    private final OrderStatusEventPublisher publisher = new OrderStatusEventPublisher(eventPublisher);

    private final Order order = Order.create(UUID.randomUUID(),
            List.of(new OrderItem(UUID.randomUUID(), "Widget", 1, new BigDecimal("10.00"))));

    @Test
    void confirmed_publishesOrderConfirmedEvent() {
        publisher.confirmed(order);

        verify(eventPublisher).publishEvent(any(OrderConfirmedEvent.class));
    }

    @Test
    void completed_publishesOrderCompletedEvent() {
        publisher.completed(order);

        verify(eventPublisher).publishEvent(any(OrderCompletedEvent.class));
    }

    @Test
    void cancelled_publishesOrderCancelledEventCarryingTheOrderId() {
        publisher.cancelled(order);

        var captor = org.mockito.ArgumentCaptor.forClass(OrderCancelledEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().orderId()).isEqualTo(order.getId());
        assertThat(captor.getValue().customerId()).isEqualTo(order.getCustomerId());
    }
}
