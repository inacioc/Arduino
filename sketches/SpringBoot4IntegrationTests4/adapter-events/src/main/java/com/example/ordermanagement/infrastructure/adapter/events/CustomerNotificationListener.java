package com.example.ordermanagement.infrastructure.adapter.events;

import com.example.ordermanagement.domain.event.OrderCancelledEvent;
import com.example.ordermanagement.domain.event.OrderCompletedEvent;
import com.example.ordermanagement.domain.event.OrderConfirmedEvent;
import com.example.ordermanagement.domain.model.Customer;
import com.example.ordermanagement.domain.port.out.CustomerRepositoryPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * Simulates emailing the customer whenever their order reaches CONFIRMED, COMPLETED or
 * CANCELLED - a second, independent {@code @ApplicationModuleListener} on the exact same
 * three events {@link OrderStatusAuditListener} already reacts to.
 * <p>
 * Deliberately reuses {@link OrderConfirmedEvent}/{@link OrderCompletedEvent}/
 * {@link OrderCancelledEvent} rather than introducing a fourth "send mail" event type: the
 * status-changing flow ({@code OrderDomainService}, {@code OrderStatusEventPublisher}) is
 * untouched by this class entirely - it neither knows nor cares that mail simulation exists.
 * Adding a reaction to an already-published event is the whole point of Modulith's
 * multi-listener model (see {@code Events_ChoiceII.md}): each listener gets its own
 * durably-tracked row and its own retry, so this one can fail or be added/removed
 * independently of the audit listener, without either affecting the other or requiring any
 * change to what gets published.
 * <p>
 * "Simulate" means: log what would have been sent, at INFO level, instead of calling a
 * real mail provider - there is no SMTP/mail-sending dependency anywhere in this project.
 * Swapping the {@code log.info} calls below for a real {@code MailSender} call is the only
 * change a genuine implementation would need; everything about durability, retry and
 * at-least-once delivery stays exactly the same, which is why this method body deliberately
 * tolerates being invoked more than once for the same event (see idempotency note below).
 */
@Component
public class CustomerNotificationListener {

    private static final Logger log = LoggerFactory.getLogger(CustomerNotificationListener.class);

    private final CustomerRepositoryPort customerRepository;

    public CustomerNotificationListener(CustomerRepositoryPort customerRepository) {
        this.customerRepository = customerRepository;
    }

    @ApplicationModuleListener
    void on(OrderConfirmedEvent event) {
        simulateEmail(event.customerId(), "Order confirmed",
                "Your order " + event.orderId() + " has been confirmed.");
    }

    @ApplicationModuleListener
    void on(OrderCompletedEvent event) {
        simulateEmail(event.customerId(), "Order completed",
                "Your order " + event.orderId() + " has been completed.");
    }

    @ApplicationModuleListener
    void on(OrderCancelledEvent event) {
        simulateEmail(event.customerId(), "Order cancelled",
                "Your order " + event.orderId() + " has been cancelled.");
    }

    /**
     * Idempotent by construction: simulated sending is just a log line, so redelivery of
     * the same event (Modulith's at-least-once guarantee - see this class's javadoc) only
     * ever produces a duplicate log line, never a duplicate real-world effect. A genuine
     * mail integration reusing this shape would need its own de-duplication (e.g. a
     * "notification already sent for this event id" check) precisely because a real send
     * does NOT have this property.
     */
    private void simulateEmail(UUID customerId, String subject, String body) {
        Optional<Customer> customer = customerRepository.findById(customerId);
        if (customer.isEmpty()) {
            // The customer existed when the order/transition was created (CreateOrderValidator
            // checks this), but could in principle have been removed since - not this
            // listener's job to fail the whole retry loop over it, just skip and log.
            log.warn("Skipping simulated email: customer {} no longer found", customerId);
            return;
        }

        log.info("Simulated email to {} <{}>: [{}] {}",
                customer.get().fullName(), customer.get().getEmail(), subject, body);
    }
}
