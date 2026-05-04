package com.example.orders.infrastructure.out.messaging;

import com.example.orders.domain.model.Money;
import com.example.orders.domain.model.Order;
import com.example.orders.domain.port.out.OrderEventPublisher;
import com.example.orders.infrastructure.out.messaging.avro.OrderCreatedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.time.ZoneOffset;
import java.util.concurrent.CompletableFuture;

@Component
public class OrderEventKafkaPublisher implements OrderEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(OrderEventKafkaPublisher.class);

    private final KafkaTemplate<String, OrderCreatedEvent> kafkaTemplate;
    private final String topicName;

    public OrderEventKafkaPublisher(KafkaTemplate<String, OrderCreatedEvent> kafkaTemplate,
                                    @Value("${kafka.topics.order-created}") String topicName) {
        this.kafkaTemplate = kafkaTemplate;
        this.topicName = topicName;
    }

    @Override
    public void publishOrderCreated(Order order) {
        OrderCreatedEvent event = toEvent(order);

        CompletableFuture<SendResult<String, OrderCreatedEvent>> future =
                kafkaTemplate.send(topicName, order.getId(), event);

        future.whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("Failed to publish OrderCreatedEvent for order {}: {}", order.getId(), ex.getMessage(), ex);
            } else {
                log.info("Published OrderCreatedEvent for order {} to partition {} offset {}",
                        order.getId(),
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset());
            }
        });
    }

    private OrderCreatedEvent toEvent(Order order) {
        Money total = order.total();
        return new OrderCreatedEvent(
                order.getId(),
                order.getCustomerId(),
                total.amount().toPlainString(),
                total.currency(),
                order.getStatus().name(),
                order.getCreatedAt().toInstant(ZoneOffset.UTC).toEpochMilli()
        );
    }
}
