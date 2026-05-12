package com.example.ordermanagement.infrastructure.adapter.out.messaging;

import com.example.ordermanagement.domain.model.Order;
import com.example.ordermanagement.domain.port.out.OrderEventPort;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
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
        try {
            String payload = objectMapper.writeValueAsString(event);
            jmsTemplate.convertAndSend(orderEventsQueue, payload);
            log.info("Published event {} for order {}", event.eventType(), event.orderId());
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize order event", e);
        }
    }
}
