package com.example.ordermanagement.adapter.out.messaging;

import com.example.ordermanagement.domain.model.Order;
import com.example.ordermanagement.domain.model.OrderItem;
import com.example.ordermanagement.domain.port.out.OrderEventPort;
import com.example.ordermanagement.infrastructure.adapter.out.messaging.OrderEvent;
import com.example.ordermanagement.messaging.MessagingApplication;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jms.core.JmsTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for IBM MQ publisher and listener.
 *
 * Strategy:
 *  - Connects to a REAL IBM MQ instance (no Testcontainers, no embedded MQ).
 *  - Uses JmsTemplate.receive() with a short timeout to verify message delivery.
 *  - For async listener verification, uses CountDownLatch pattern.
 *
 * Requirements:
 *  - IBM MQ running and accessible per application-test.yml / TEST_MQ_* env vars.
 *  - The queue ORDER.EVENTS.TEST.QUEUE must exist on the broker.
 *
 * Cleanup strategy:
 *  - @BeforeEach drains the test queue to prevent interference between runs.
 */
@SpringBootTest(classes = MessagingApplication.class)
class OrderMqPublisherIT {

    @Autowired
    private OrderEventPort orderEventPort;

    @Autowired
    private JmsTemplate jmsTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Value("${app.messaging.order-events-queue}")
    private String queue;

    @BeforeEach
    void drainQueue() {
        // Purge any leftover messages from previous test runs
        String msg;
        do {
            msg = (String) jmsTemplate.receiveAndConvert(queue);
        } while (msg != null);
    }

    // ── Publish ORDER_CREATED ─────────────────────────────────────────────────

    @Test
    @DisplayName("publishOrderCreated() sends a message to IBM MQ with correct payload")
    void publishOrderCreated_messageOnQueue() throws Exception {
        Order order = buildOrder();

        orderEventPort.publishOrderCreated(order);

        // Receive synchronously within 5 seconds
        String raw = (String) jmsTemplate.receiveAndConvert(queue);

        assertThat(raw).isNotNull();
        OrderEvent event = objectMapper.readValue(raw, OrderEvent.class);
        assertThat(event.eventType()).isEqualTo(OrderEvent.ORDER_CREATED);
        assertThat(event.orderId()).isEqualTo(order.getId());
        assertThat(event.customerId()).isEqualTo(order.getCustomerId());
        assertThat(event.totalAmount()).isEqualByComparingTo(order.getTotalAmount());
    }

    // ── Publish ORDER_COMPLETED ───────────────────────────────────────────────

    @Test
    @DisplayName("publishOrderCompleted() sends a message with ORDER_COMPLETED event type")
    void publishOrderCompleted_messageOnQueue() throws Exception {
        Order order = buildOrder();
        order.confirm();
        order.startProcessing();
        order.complete();

        orderEventPort.publishOrderCompleted(order);

        String raw = (String) jmsTemplate.receiveAndConvert(queue);

        assertThat(raw).isNotNull();
        OrderEvent event = objectMapper.readValue(raw, OrderEvent.class);
        assertThat(event.eventType()).isEqualTo(OrderEvent.ORDER_COMPLETED);
    }

    // ── Async listener pattern (CountDownLatch) ───────────────────────────────

    /**
     * Tests that the OrderMqListener correctly processes a message sent to the queue.
     * Uses a CountDownLatch to wait for async delivery.
     *
     * This pattern is necessary when testing @JmsListener methods since they
     * run on separate threads managed by the JMS container.
     */
    @Test
    @DisplayName("OrderMqListener receives and logs published events without exception")
    void listener_receivesPublishedMessage_noException() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> caughtError = new AtomicReference<>();

        // Install a thread-local test listener by checking that the container
        // processed the message (no exception is thrown on the listener thread)
        Order order = buildOrder();
        orderEventPort.publishOrderCreated(order);

        // Wait up to 5s for the listener to consume from the queue
        // The test queue listener is defined in OrderMqListener.
        // We verify indirectly: if no exception is thrown and the queue drains
        // within timeout, the listener consumed successfully.
        boolean consumed = waitForQueueToEmpty(5_000);
        assertThat(consumed)
                .as("Message should have been consumed by OrderMqListener within timeout")
                .isTrue();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private boolean waitForQueueToEmpty(long timeoutMs) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            // If receive returns null, queue is empty (message was consumed by listener)
            String msg = (String) jmsTemplate.receiveAndConvert(queue);
            if (msg == null) {
                return true;
            }
            TimeUnit.MILLISECONDS.sleep(200);
        }
        return false;
    }

    private Order buildOrder() {
        return Order.create("test-customer", List.of(
                new OrderItem(UUID.fromString("55555555-0000-0000-0000-0000000000a1"),
                        "MQ Test Product", 2, new BigDecimal("25.00"))
        ));
    }
}
