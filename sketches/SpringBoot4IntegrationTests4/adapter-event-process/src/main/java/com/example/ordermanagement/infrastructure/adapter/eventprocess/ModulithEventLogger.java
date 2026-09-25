package com.example.ordermanagement.infrastructure.adapter.eventprocess;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.modulith.events.core.EventPublicationRegistry;
import org.springframework.modulith.events.core.TargetEventPublication;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Collection;

/**
 * Generic, read-only observer of Spring Modulith's event-publication outbox: every
 * {@code app.event-process.fixed-rate-ms} (default 15s), logs every publication still
 * marked incomplete in the shared {@code event_publication} table — whatever its event
 * type, from whichever application published it.
 * <p>
 * Deliberately never calls {@code markCompleted(...)}. Modulith tracks completion per
 * listener/target identifier, and {@code adapter-out-batch}'s
 * {@code OrderConfirmationRequestPoller} already owns completing
 * {@code OrderCreatedIntegrationEvent} publications — itself a non-standard use of the
 * registry as a cross-process outbox (see that class's javadoc). A second consumer
 * racing to complete the same row would non-deterministically "steal" it from whichever
 * process gets there first. Staying read-only makes this component safe to add to ANY
 * application on the same database without changing what anything else observes or
 * completes: it's a pure audit/diagnostic tap, not a competing consumer.
 * <p>
 * Requires {@code @EnableScheduling} somewhere in the host application context — this
 * class deliberately doesn't declare it itself, so embedding this component into an app
 * that must stay a one-shot process (see {@code adapter-out-batch}'s own
 * {@code SchedulingConfig} for why that distinction matters there) doesn't silently turn
 * it into a long-running one. {@link EventProcessApplication} in this same module
 * enables it for the standalone case.
 */
@Component
@ConditionalOnProperty(name = "app.event-process.enabled", matchIfMissing = true)
public class ModulithEventLogger {

    private static final Logger log = LoggerFactory.getLogger(ModulithEventLogger.class);

    private final EventPublicationRegistry eventPublications;

    public ModulithEventLogger(EventPublicationRegistry eventPublications) {
        this.eventPublications = eventPublications;
    }

    @Scheduled(fixedRateString = "${app.event-process.fixed-rate-ms:15000}")
    public void logIncompletePublications() {
        Collection<TargetEventPublication> pending = eventPublications.findIncompletePublications();

        if (pending.isEmpty()) {
            log.debug("No incomplete event publications");
            return;
        }

        for (TargetEventPublication publication : pending) {
            Object event = publication.getEvent();
            log.info("Event publication pending: type={} target={} publicationDate={} payload={}",
                    event.getClass().getSimpleName(),
                    publication.getTargetIdentifier().getValue(),
                    publication.getPublicationDate(),
                    event);
        }
    }
}
