package com.example.ordermanagement.infrastructure.adapter.events;

import com.example.ordermanagement.domain.event.OrderCancelledEvent;
import com.example.ordermanagement.domain.event.OrderConfirmedEvent;
import com.example.ordermanagement.support.IntegrationTestBase;
import com.example.ordermanagement.support.JwtHelper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.modulith.test.PublishedEvents;
import org.springframework.modulith.test.PublishedEventsExtension;
import org.springframework.test.context.jdbc.Sql;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end proof that confirming/cancelling an order through the real REST API actually
 * publishes the corresponding Modulith module event in this process - the piece that a
 * plain unit test (mocking {@code ApplicationEventPublisher}) can't demonstrate, since it
 * needs the real Spring context, the JPA-backed {@code EventPublicationRegistry}, and the
 * {@code event_publication} table (see {@code V4__create_event_publication_table.sql}).
 * <p>
 * Uses {@link PublishedEventsExtension} (from {@code spring-modulith-starter-test}) rather
 * than a hand-rolled capturing {@code ApplicationListener} bean - it registers itself
 * against the real application context and hands back every event actually published
 * during the test, typed and ready to assert on.
 */
@ExtendWith(PublishedEventsExtension.class)
@Sql(scripts = {"/sql/clean-orders.sql", "/sql/insert-test-orders.sql"},
        executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class OrderStatusModuleEventsIT extends IntegrationTestBase {

    private static final UUID PENDING_ORDER_ID = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");

    @Test
    @DisplayName("PUT /api/orders/{id}/confirm publishes OrderConfirmedEvent for that order")
    void confirmOrder_publishesOrderConfirmedEvent(PublishedEvents events) throws Exception {
        mockMvc.perform(put("/api/orders/{id}/confirm", PENDING_ORDER_ID)
                        .with(JwtHelper.adminToken()))
                .andExpect(status().isOk());

        assertThat(events.ofType(OrderConfirmedEvent.class))
                .extracting(OrderConfirmedEvent::orderId)
                .containsExactly(PENDING_ORDER_ID);
    }

    @Test
    @DisplayName("PUT /api/orders/{id}/cancel publishes OrderCancelledEvent for that order")
    void cancelOrder_publishesOrderCancelledEvent(PublishedEvents events) throws Exception {
        mockMvc.perform(put("/api/orders/{id}/cancel", PENDING_ORDER_ID)
                        .with(JwtHelper.customerToken("customer-1")))
                .andExpect(status().isOk());

        assertThat(events.ofType(OrderCancelledEvent.class))
                .extracting(OrderCancelledEvent::orderId)
                .containsExactly(PENDING_ORDER_ID);
    }
}
