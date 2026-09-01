package com.example.ordermanagement.infrastructure.adapter.out.messaging;

import tools.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.stereotype.Component;

@Component
public class OrderMqListener {

    private static final Logger log = LoggerFactory.getLogger(OrderMqListener.class);

    private final ObjectMapper objectMapper;

    public OrderMqListener(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @JmsListener(destination = "${app.messaging.order-events-queue}",
                 containerFactory = "jmsListenerContainerFactory")
    public void onOrderEvent(String payload) {
        try {
            OrderEvent event = objectMapper.readValue(payload, OrderEvent.class);
            log.info("Received event type={} orderId={} status={}",
                    event.eventType(), event.orderId(), event.status());
            // In a real system: dispatch to downstream handlers, audit log, notify, etc.
        } catch (Exception e) {
            log.error("Failed to process order event: {}", payload, e);
        }
    }
}
