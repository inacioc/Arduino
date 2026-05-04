package com.example.ordermanagement.infrastructure.adapter.out.messaging;

import com.example.ordermanagement.domain.model.Order;
import com.example.ordermanagement.domain.port.out.NotificationPort;
import com.example.ordermanagement.infrastructure.adapter.out.messaging.dto.OrderEventMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.stereotype.Component;

@Component
public class IbmMqOrderPublisher implements NotificationPort {

    private static final Logger log = LoggerFactory.getLogger(IbmMqOrderPublisher.class);

    static final String QUEUE_ORDER_CREATED = "ORDER.CREATED";
    static final String QUEUE_ORDER_STATUS  = "ORDER.STATUS.CHANGED";

    private final JmsTemplate jmsTemplate;

    public IbmMqOrderPublisher(JmsTemplate jmsTemplate) {
        this.jmsTemplate = jmsTemplate;
    }

    @Override
    public void sendOrderCreatedNotification(Order order) {
        OrderEventMessage message = OrderEventMessage.from(order, "ORDER_CREATED");
        log.info("Publishing ORDER_CREATED for orderId={}", order.getId());
        jmsTemplate.convertAndSend(QUEUE_ORDER_CREATED, message);
    }

    @Override
    public void sendOrderStatusChangedNotification(Order order) {
        OrderEventMessage message = OrderEventMessage.from(order, "ORDER_STATUS_CHANGED");
        log.info("Publishing ORDER_STATUS_CHANGED for orderId={} status={}", order.getId(), order.getStatus());
        jmsTemplate.convertAndSend(QUEUE_ORDER_STATUS, message);
    }
}
