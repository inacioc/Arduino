package com.example.ordermanagement.integration.service;

import com.example.ordermanagement.BaseIntegrationTest;
import com.example.ordermanagement.application.service.OrderNotFoundException;
import com.example.ordermanagement.domain.model.Order;
import com.example.ordermanagement.domain.model.OrderStatus;
import com.example.ordermanagement.domain.port.in.CreateOrderUseCase;
import com.example.ordermanagement.domain.port.in.GetOrderUseCase;
import com.example.ordermanagement.domain.port.in.UpdateOrderStatusUseCase;
import com.example.ordermanagement.domain.port.out.NotificationPort;
import com.example.ordermanagement.domain.port.out.PricingServicePort;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Service-layer integration test.
 *
 * - Uses real OrderPersistenceAdapter → embedded PostgreSQL (via BaseIntegrationTest)
 * - PricingServicePort  mocked  → no external HTTP call
 * - NotificationPort    spied on → verify publishing side-effects
 *
 * This validates the full application orchestration logic:
 * create → price → save → notify
 */
class OrderApplicationServiceIT extends BaseIntegrationTest {

    @Autowired
    private CreateOrderUseCase createOrderUseCase;

    @Autowired
    private GetOrderUseCase getOrderUseCase;

    @Autowired
    private UpdateOrderStatusUseCase updateOrderStatusUseCase;

    @MockitoBean
    private PricingServicePort pricingServicePort;

    // SpyBean wraps the real IbmMqOrderPublisher so we can verify calls
    // but still record side-effects (messages sent to embedded Artemis)
    @MockitoSpyBean
    private NotificationPort notificationPort;

    // ── Create order ────────────────────────────────────────────────────────

    @Test
    void createOrder_callsPricingService_andPersists_andNotifies() {
        when(pricingServicePort.calculateTotalPrice(any())).thenReturn(new BigDecimal("99.99"));

        var command = new CreateOrderUseCase.CreateOrderCommand(
                "customer-service-it",
                List.of(new CreateOrderUseCase.CreateOrderCommand.OrderItemDto("P1", "Widget", 3))
        );

        Order created = createOrderUseCase.createOrder(command);

        // Persisted with pricing applied
        assertThat(created.getId()).isNotNull();
        assertThat(created.getCustomerId()).isEqualTo("customer-service-it");
        assertThat(created.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(created.getTotalPrice()).isEqualByComparingTo("99.99");

        // Pricing service was consulted exactly once
        verify(pricingServicePort, times(1)).calculateTotalPrice(any());

        // Notification was published
        ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
        verify(notificationPort).sendOrderCreatedNotification(captor.capture());
        assertThat(captor.getValue().getId()).isEqualTo(created.getId());
    }

    @Test
    void createOrder_resultIsRetrievableById() {
        when(pricingServicePort.calculateTotalPrice(any())).thenReturn(BigDecimal.TEN);

        Order created = createOrderUseCase.createOrder(new CreateOrderUseCase.CreateOrderCommand(
                "customer-retrieve-test",
                List.of(new CreateOrderUseCase.CreateOrderCommand.OrderItemDto("P2", "Gadget", 1))
        ));

        Optional<Order> fetched = getOrderUseCase.getOrderById(created.getId());

        assertThat(fetched).isPresent();
        assertThat(fetched.get().getCustomerId()).isEqualTo("customer-retrieve-test");
        assertThat(fetched.get().getItems()).hasSize(1);
    }

    // ── Confirm order ───────────────────────────────────────────────────────

    @Test
    void confirmOrder_transitionsPendingToConfirmed_andPublishesStatusChange() {
        when(pricingServicePort.calculateTotalPrice(any())).thenReturn(BigDecimal.TEN);

        Order order = createOrderUseCase.createOrder(new CreateOrderUseCase.CreateOrderCommand(
                "customer-confirm-test",
                List.of(new CreateOrderUseCase.CreateOrderCommand.OrderItemDto("P3", "Item", 1))
        ));

        Order confirmed = updateOrderStatusUseCase.confirmOrder(order.getId());

        assertThat(confirmed.getStatus()).isEqualTo(OrderStatus.CONFIRMED);

        // Verify status-change notification was sent
        verify(notificationPort).sendOrderStatusChangedNotification(
                argThat(o -> o.getStatus() == OrderStatus.CONFIRMED)
        );
    }

    @Test
    void confirmOrder_nonExistentId_throwsOrderNotFoundException() {
        UUID nonExistent = UUID.randomUUID();

        assertThatThrownBy(() -> updateOrderStatusUseCase.confirmOrder(nonExistent))
                .isInstanceOf(OrderNotFoundException.class)
                .hasMessageContaining(nonExistent.toString());
    }

    // ── Cancel order ────────────────────────────────────────────────────────

    @Test
    void cancelOrder_fromPending_succeeds() {
        when(pricingServicePort.calculateTotalPrice(any())).thenReturn(BigDecimal.TEN);

        Order order = createOrderUseCase.createOrder(new CreateOrderUseCase.CreateOrderCommand(
                "customer-cancel-test",
                List.of(new CreateOrderUseCase.CreateOrderCommand.OrderItemDto("P4", "Item", 1))
        ));

        Order cancelled = updateOrderStatusUseCase.cancelOrder(order.getId());

        assertThat(cancelled.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    void cancelOrder_confirmedThenCancel_isPossible() {
        when(pricingServicePort.calculateTotalPrice(any())).thenReturn(BigDecimal.TEN);

        Order order = createOrderUseCase.createOrder(new CreateOrderUseCase.CreateOrderCommand(
                "customer-cancel-confirmed",
                List.of(new CreateOrderUseCase.CreateOrderCommand.OrderItemDto("P5", "Item", 2))
        ));
        updateOrderStatusUseCase.confirmOrder(order.getId());

        Order cancelled = updateOrderStatusUseCase.cancelOrder(order.getId());

        assertThat(cancelled.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    // ── Get orders by customer ──────────────────────────────────────────────

    @Test
    void getOrdersByCustomer_returnsOnlyThatCustomersOrders() {
        when(pricingServicePort.calculateTotalPrice(any())).thenReturn(BigDecimal.TEN);

        // Create 2 orders for target customer and 1 for another
        createOrderUseCase.createOrder(new CreateOrderUseCase.CreateOrderCommand(
                "customer-multi-1",
                List.of(new CreateOrderUseCase.CreateOrderCommand.OrderItemDto("P6", "Item", 1))
        ));
        createOrderUseCase.createOrder(new CreateOrderUseCase.CreateOrderCommand(
                "customer-multi-1",
                List.of(new CreateOrderUseCase.CreateOrderCommand.OrderItemDto("P7", "Item", 2))
        ));
        createOrderUseCase.createOrder(new CreateOrderUseCase.CreateOrderCommand(
                "customer-other",
                List.of(new CreateOrderUseCase.CreateOrderCommand.OrderItemDto("P8", "Item", 1))
        ));

        var orders = getOrderUseCase.getOrdersByCustomer("customer-multi-1");

        assertThat(orders).hasSize(2);
        assertThat(orders).allMatch(o -> o.getCustomerId().equals("customer-multi-1"));
    }
}
