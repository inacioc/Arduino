package com.example.ordermanagement.infrastructure.adapter.events;

import com.example.ordermanagement.domain.event.OrderCancelledEvent;
import com.example.ordermanagement.domain.event.OrderCompletedEvent;
import com.example.ordermanagement.domain.event.OrderConfirmedEvent;
import com.example.ordermanagement.domain.model.Customer;
import com.example.ordermanagement.domain.port.out.CustomerRepositoryPort;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CustomerNotificationListenerTest {

    private final CustomerRepositoryPort customerRepository = mock(CustomerRepositoryPort.class);
    private final CustomerNotificationListener listener = new CustomerNotificationListener(customerRepository);

    private final UUID customerId = UUID.randomUUID();
    private final Customer customer =
            Customer.reconstitute(customerId, "Ada", "Lovelace", "555-0100", "ada@example.com");

    @Test
    void onOrderConfirmed_looksUpCustomerAndDoesNotThrow() {
        when(customerRepository.findById(customerId)).thenReturn(Optional.of(customer));

        assertThatCode(() -> listener.on(new OrderConfirmedEvent(UUID.randomUUID(), customerId, LocalDateTime.now())))
                .doesNotThrowAnyException();
    }

    @Test
    void onOrderCompleted_looksUpCustomerAndDoesNotThrow() {
        when(customerRepository.findById(customerId)).thenReturn(Optional.of(customer));

        assertThatCode(() -> listener.on(new OrderCompletedEvent(UUID.randomUUID(), customerId, LocalDateTime.now())))
                .doesNotThrowAnyException();
    }

    @Test
    void onOrderCancelled_looksUpCustomerAndDoesNotThrow() {
        when(customerRepository.findById(customerId)).thenReturn(Optional.of(customer));

        assertThatCode(() -> listener.on(new OrderCancelledEvent(UUID.randomUUID(), customerId, LocalDateTime.now())))
                .doesNotThrowAnyException();
    }

    @Test
    void missingCustomer_isSkippedWithoutThrowing() {
        when(customerRepository.findById(customerId)).thenReturn(Optional.empty());

        assertThatCode(() -> listener.on(new OrderConfirmedEvent(UUID.randomUUID(), customerId, LocalDateTime.now())))
                .doesNotThrowAnyException();
    }
}
