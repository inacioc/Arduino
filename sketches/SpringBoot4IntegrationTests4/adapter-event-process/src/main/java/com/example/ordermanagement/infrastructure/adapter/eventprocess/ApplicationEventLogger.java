package com.example.ordermanagement.infrastructure.adapter.eventprocess;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * Real-time, in-process complement to {@link ModulithEventLogger}: reacts to any event
 * Spring Modulith publishes in THIS process via a plain {@code @ApplicationModuleListener},
 * instead of waiting for the next poll.
 * <p>
 * Listening on {@code Object} rather than one concrete event type is deliberate: this
 * module doesn't own a fixed vocabulary the way {@code adapter-events} owns
 * {@code OrderCreatedIntegrationEvent} - it logs whatever passes through, present and
 * future, matching the generic contract {@link ModulithEventLogger} already offers for
 * the polling case. Spring resolves listener dispatch by the method's declared parameter
 * type, so a method typed to {@code Object} receives every event published through
 * {@code ApplicationEventPublisher} in this process, not just ones sharing a common
 * superclass. Nothing here needs to know the shape of what it's logging.
 * <p>
 * Unlike {@link ModulithEventLogger}, this needs no "never completes anything" caveat:
 * Modulith marks a listener's own {@code event_publication} row completed automatically
 * once its method returns without throwing, and each {@code @ApplicationModuleListener}
 * method gets its own row - so this one adding a new row alongside
 * {@code adapter-events}'s {@code OrderCreatedEventListener} (if both happen to be on the
 * same application's classpath) doesn't race or interfere with either that listener or
 * with {@code adapter-out-batch}'s poller.
 * <p>
 * Only fires for events published in the same JVM this bean runs in - it gives no
 * cross-process visibility the way polling does. Embedding this module into the actual
 * publishing application (e.g. adapter-in-web) is what makes it useful; running
 * {@link com.example.ordermanagement.eventprocess.EventProcessApplication} standalone,
 * this listener is inert - nothing in that separate process ever publishes an event for
 * it to hear, which is exactly why {@link ModulithEventLogger}'s polling exists too.
 * <p>
 * One general {@code @TransactionalEventListener}-family caveat worth knowing: this only
 * fires for events published from inside a transaction that actually commits (the
 * default {@code AFTER_COMMIT} phase). An event published with no transaction active is
 * silently never delivered here.
 */
@Component
@ConditionalOnProperty(name = "app.event-process.listener.enabled", matchIfMissing = true)
public class ApplicationEventLogger {

    private static final Logger log = LoggerFactory.getLogger(ApplicationEventLogger.class);

    @ApplicationModuleListener
    void on(Object event) {
        log.info("Event received: type={} payload={}", event.getClass().getSimpleName(), event);
    }
}
