package com.example.ordermanagement.domain.port.out;

import com.example.ordermanagement.domain.model.Order;

/**
 * Outbound port for in-process notifications of order status transitions.
 * <p>
 * This is deliberately a separate port from {@link OrderEventPort}: that one publishes the
 * cross-process integration event to IBM MQ for other deployable applications, while this
 * one is for side effects that only ever need to happen inside the same application
 * process that made the transition (e.g. an audit trail). Its adapter implementation
 * publishes through Spring's {@code ApplicationEventPublisher} — see
 * {@code OrderStatusEventPublisher} in whichever app wires it up — so any
 * {@code @ApplicationModuleListener} in that same process can react without domain ever
 * depending on Spring's eventing API directly.
 */
public interface OrderStatusEventPort {

    void confirmed(Order order);

    void completed(Order order);

    void cancelled(Order order);
}
