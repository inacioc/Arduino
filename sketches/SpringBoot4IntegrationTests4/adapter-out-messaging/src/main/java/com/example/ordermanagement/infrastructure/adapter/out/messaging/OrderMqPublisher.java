package com.example.ordermanagement.infrastructure.adapter.out.messaging;

import com.example.ordermanagement.domain.exception.InfrastructureUnavailableException;
import com.example.ordermanagement.domain.model.Order;
import com.example.ordermanagement.domain.port.out.OrderEventPort;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jms.JmsException;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class OrderMqPublisher implements OrderEventPort {

    private static final Logger log = LoggerFactory.getLogger(OrderMqPublisher.class);

    private final JmsTemplate jmsTemplate;
    private final ObjectMapper objectMapper;

    @Value("${app.messaging.order-events-queue}")
    private String orderEventsQueue;

    public OrderMqPublisher(JmsTemplate jmsTemplate, ObjectMapper objectMapper) {
        this.jmsTemplate  = jmsTemplate;
        this.objectMapper  = objectMapper;
    }

    @Override
    public void publishOrderCreated(Order order) {
        publish(new OrderEvent(
                OrderEvent.ORDER_CREATED,
                order.getId(),
                order.getCustomerId(),
                order.getStatus(),
                order.getTotalAmount(),
                LocalDateTime.now()
        ));
    }

    @Override
    public void publishOrderCompleted(Order order) {
        publish(new OrderEvent(
                OrderEvent.ORDER_COMPLETED,
                order.getId(),
                order.getCustomerId(),
                order.getStatus(),
                order.getTotalAmount(),
                LocalDateTime.now()
        ));
    }

    private void publish(OrderEvent event) {
        String payload;
        try {
            payload = objectMapper.writeValueAsString(event);
        } catch (JacksonException e) {
            // Not a broker outage, but the caller can't do anything about it either -
            // folded into the same InfrastructureUnavailableException vocabulary as a
            // broker outage (see that class's javadoc) rather than a bare RuntimeException
            // that GlobalExceptionHandler would have no domain-meaningful way to map.
            throw new InfrastructureUnavailableException(
                    "Failed to serialize order event " + event.eventType() + " for order " + event.orderId(), e);
        }
        try {
            jmsTemplate.convertAndSend(orderEventsQueue, payload);
            log.info("Published event {} for order {}", event.eventType(), event.orderId());
        } catch (JmsException e) {
            log.error("Failed to publish {} for order {} to IBM MQ", event.eventType(), event.orderId(), e);
            throw new InfrastructureUnavailableException(
                    "The order events broker is temporarily unavailable. Please try again later.", e);
        }
    }
}
