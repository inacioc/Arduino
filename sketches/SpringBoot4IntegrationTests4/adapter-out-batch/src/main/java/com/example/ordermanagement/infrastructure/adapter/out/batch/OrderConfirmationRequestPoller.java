package com.example.ordermanagement.infrastructure.adapter.out.batch;

import com.example.ordermanagement.infrastructure.adapter.events.OrderConfirmationRequest;
import com.example.ordermanagement.infrastructure.adapter.events.OrderConfirmationRequestRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
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
 * Every {@code app.batch.scheduler.fixed-rate-ms} (default 60s), writes a CSV file — in
 * the same {@code orderId,targetStatus} shape {@link OrderBatchConfig}'s file-driven job
 * reads — for every order queued via the event-driven outbox
 * ({@link OrderConfirmationRequest}), then marks those rows processed.
 * <p>
 * Deliberately does not launch {@link OrderBatchConfig#JOB_NAME} itself: running the
 * generated file through the batch job stays a separate step for now, so a failed/slow
 * job run can't be conflated with a failed poll. Active only under the {@code scheduler}
 * profile — see {@link SchedulingConfig}.
 */
@Component
@Profile("scheduler")
public class OrderConfirmationRequestPoller {

    private static final Logger log = LoggerFactory.getLogger(OrderConfirmationRequestPoller.class);
    private static final String CSV_HEADER = "orderId,targetStatus";

    private final OrderConfirmationRequestRepository confirmationRequests;
    private final Path outputDir;

    public OrderConfirmationRequestPoller(
            OrderConfirmationRequestRepository confirmationRequests,
            @Value("${app.batch.scheduler.output-dir:./batch-output}") String outputDir) {
        this.confirmationRequests = confirmationRequests;
        this.outputDir = Path.of(outputDir);
    }

    @Scheduled(fixedRateString = "${app.batch.scheduler.fixed-rate-ms:60000}")
    public void poll() {
        List<OrderConfirmationRequest> pending = confirmationRequests.findAllByProcessedFalse();
        if (pending.isEmpty()) {
            log.debug("No pending order-confirmation requests");
            return;
        }

        Path file = writeFile(pending);

        pending.forEach(OrderConfirmationRequest::markProcessed);
        confirmationRequests.saveAll(pending);

        log.info("Wrote {} order-confirmation row(s) to {}", pending.size(), file);
    }

    private Path writeFile(List<OrderConfirmationRequest> pending) {
        try {
            Files.createDirectories(outputDir);
            Path file = outputDir.resolve("orders-confirm-" + Instant.now().toEpochMilli() + ".csv");

            StringBuilder csv = new StringBuilder(CSV_HEADER).append('\n');
            for (OrderConfirmationRequest request : pending) {
                csv.append(request.getOrderId()).append(',').append(request.getTargetStatus()).append('\n');
            }
            Files.writeString(file, csv.toString(), StandardCharsets.UTF_8);
            return file;
        } catch (IOException e) {
            // Nothing gets marked processed on failure, so the same rows are retried next cycle.
            throw new UncheckedIOException("Failed to write order-confirmation file", e);
        }
    }
}
