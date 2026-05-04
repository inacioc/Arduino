package com.example.ordermanagement.infrastructure.adapter.out.messaging;

import com.example.ordermanagement.infrastructure.adapter.out.messaging.dto.OrderEventMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.stereotype.Component;

/**
 * Listens for external order update events arriving via IBM MQ.
 * In tests this consumer runs against embedded Artemis (same JMS API).
 */
@Component
public class IbmMqOrderConsumer {

    private static final Logger log = LoggerFactory.getLogger(IbmMqOrderConsumer.class);

    @JmsListener(destination = "ORDER.EXTERNAL.UPDATES", containerFactory = "jmsListenerContainerFactory")
    public void handleExternalOrderUpdate(OrderEventMessage message) {
        log.info("Received external order update: orderId={} eventType={}", message.orderId(), message.eventType());
        // Domain processing would go here
    }
}
