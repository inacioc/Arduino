package com.example.ordermanagement.infrastructure.adapter.out.batch;

import com.example.ordermanagement.domain.event.OrderCreatedIntegrationEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.modulith.events.core.EventPublicationRegistry;
import org.springframework.modulith.events.core.TargetEventPublication;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

/**
 * Every {@code app.batch.scheduler.fixed-rate-ms} (default 60s), reads
 * {@code OrderCreatedIntegrationEvent} publications straight out of Spring Modulith's
 * standard {@code event_publication} table via {@link EventPublicationRegistry} — the
 * same table {@code adapter-in-web}'s {@code OrderCreatedEventListener} caused to be
 * populated, in a different process, sharing only the Postgres database — writes a CSV
 * file in the same {@code orderId,targetStatus} shape {@link OrderBatchConfig}'s
 * file-driven job reads, then marks those publications completed itself.
 * <p>
 * Deliberately does not launch {@link OrderBatchConfig#JOB_NAME}: running the generated
 * file through the batch job stays a separate step for now. Active only under the
 * {@code scheduler} profile — see {@link SchedulingConfig}.
 * <p>
 * Reading and completing another application's event publications this way is a
 * deliberate, non-standard use of Modulith's internal registry as a cross-process
 * outbox — see the discussion that led to this design for the tradeoffs (schema
 * coupling between adapter-in-web and adapter-out-batch across Modulith versions).
 */
@Component
@Profile("scheduler")
public class OrderConfirmationRequestPoller {

    private static final Logger log = LoggerFactory.getLogger(OrderConfirmationRequestPoller.class);
    private static final String CSV_HEADER = "orderId,targetStatus";
    private static final String TARGET_STATUS = "CONFIRMED";

    private final EventPublicationRegistry eventPublications;
    private final Path outputDir;

    public OrderConfirmationRequestPoller(
            EventPublicationRegistry eventPublications,
            @Value("${app.batch.scheduler.output-dir:./batch-output}") String outputDir) {
        this.eventPublications = eventPublications;
        this.outputDir = Path.of(outputDir);
    }

    @Scheduled(fixedRateString = "${app.batch.scheduler.fixed-rate-ms:60000}")
    public void poll() {
        List<TargetEventPublication> pending = eventPublications.findIncompletePublications().stream()
                .filter(p -> p.getEvent() instanceof OrderCreatedIntegrationEvent)
                .toList();

        if (pending.isEmpty()) {
            log.debug("No pending OrderCreatedIntegrationEvent publications");
            return;
        }

        Path file = writeFile(pending);
        pending.forEach(p -> eventPublications.markCompleted(p.getEvent(), p.getTargetIdentifier()));

        log.info("Wrote {} order-confirmation row(s) to {}", pending.size(), file);
    }

    private Path writeFile(List<TargetEventPublication> pending) {
        try {
            Files.createDirectories(outputDir);
            Path file = outputDir.resolve("orders-confirm-" + Instant.now().toEpochMilli() + ".csv");

            StringBuilder csv = new StringBuilder(CSV_HEADER).append('\n');
            for (TargetEventPublication publication : pending) {
                OrderCreatedIntegrationEvent event = (OrderCreatedIntegrationEvent) publication.getEvent();
                csv.append(event.orderId()).append(',').append(TARGET_STATUS).append('\n');
            }
            Files.writeString(file, csv.toString(), StandardCharsets.UTF_8);
            return file;
        } catch (IOException e) {
            // Nothing gets marked completed on failure, so the same publications are retried next cycle.
            throw new UncheckedIOException("Failed to write order-confirmation file", e);
        }
    }
}
