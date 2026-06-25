package com.example.ordermanagement.adapter.out.batch;

import com.example.ordermanagement.domain.model.Order;
import com.example.ordermanagement.domain.model.OrderItem;
import com.example.ordermanagement.domain.model.OrderStatus;
import com.example.ordermanagement.domain.port.out.OrderRepositoryPort;
import com.example.ordermanagement.support.IntegrationTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.test.JobLauncherTestUtils;
import org.springframework.batch.test.context.SpringBatchTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.jdbc.Sql;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for the file-driven status-change batch job.
 *
 * <p>The job reads {@code orderId,targetStatus} rows from an input CSV, attempts to
 * advance each order to the target status (chaining through intermediate states),
 * and writes {@code id,presentStatus,result} rows to an output CSV.
 *
 * <p>Strategy:
 *  - @SpringBatchTest provides JobLauncherTestUtils to launch the job with parameters.
 *  - Real PostgreSQL for both domain data and batch metadata.
 *  - Input file is written to a @TempDir; the output file is read back and asserted.
 */
@SpringBatchTest
@Sql(scripts = "/sql/clean-orders.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class OrderBatchJobIT extends IntegrationTestBase {

    @Autowired
    private JobLauncherTestUtils jobLauncherTestUtils;

    @Autowired
    private OrderRepositoryPort orderRepository;

    @Test
    @DisplayName("Job advances orders to their target status and reports OK")
    void job_changesStatuses_writesOkResults(@TempDir Path tempDir) throws Exception {
        // Arrange: a PENDING order to complete, and a CONFIRMED order to cancel
        Order toComplete = saveOrder("cust-complete", OrderStatus.PENDING);
        Order toCancel   = saveOrder("cust-cancel", OrderStatus.CONFIRMED);

        Path input = writeInput(tempDir,
                row(toComplete.getId(), "COMPLETED"),
                row(toCancel.getId(),   "CANCELLED"));
        Path output = tempDir.resolve("out.csv");

        // Act
        JobExecution execution = launch(input, output);

        // Assert: job + DB state
        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertStatus(toComplete.getId(), OrderStatus.COMPLETED);
        assertStatus(toCancel.getId(),   OrderStatus.CANCELLED);

        // Assert: output CSV
        List<String> lines = Files.readAllLines(output);
        assertThat(lines.get(0)).isEqualTo("id,presentStatus,result");
        assertThat(lines).contains(
                "%s,COMPLETED,OK".formatted(toComplete.getId()),
                "%s,CANCELLED,OK".formatted(toCancel.getId()));
    }

    @Test
    @DisplayName("Job reports error codes for bad rows without failing the job")
    void job_badRows_reportErrorCodes(@TempDir Path tempDir) throws Exception {
        Order completed = saveOrder("cust-completed", OrderStatus.COMPLETED);
        UUID missingId  = UUID.randomUUID();

        Path input = writeInput(tempDir,
                row(completed.getId(), "CONFIRMED"),   // illegal: cannot go backwards
                row(missingId,         "CONFIRMED"),   // no such order
                "not-a-uuid,CONFIRMED",                 // unparseable id
                row(completed.getId(), "FOO"));         // unknown target status
        Path output = tempDir.resolve("out.csv");

        JobExecution execution = launch(input, output);

        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        // completed order is untouched
        assertStatus(completed.getId(), OrderStatus.COMPLETED);

        List<String> lines = Files.readAllLines(output);
        assertThat(lines).contains(
                "%s,COMPLETED,INVALID_TRANSITION".formatted(completed.getId()),
                "%s,,ORDER_NOT_FOUND".formatted(missingId),
                "not-a-uuid,,INVALID_ID",
                "%s,,INVALID_TARGET_STATUS".formatted(completed.getId()));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private JobExecution launch(Path input, Path output) throws Exception {
        JobParameters params = new JobParametersBuilder()
                .addString("inputFile", input.toString())
                .addString("outputFile", output.toString())
                .toJobParameters();
        return jobLauncherTestUtils.launchJob(params);
    }

    private Order saveOrder(String customerId, OrderStatus target) {
        Order order = Order.create(customerId, List.of(
                new OrderItem(UUID.fromString("44444444-0000-0000-0000-0000000000ba"),
                        "Batch Test Product", 1, new BigDecimal("100.00"))
        ));
        order.advanceTo(target);
        return orderRepository.save(order);
    }

    private static String row(UUID orderId, String targetStatus) {
        return orderId + "," + targetStatus;
    }

    private Path writeInput(Path dir, String... rows) throws Exception {
        Path input = dir.resolve("in.csv");
        StringBuilder sb = new StringBuilder("orderId,targetStatus\n");
        for (String r : rows) {
            sb.append(r).append('\n');
        }
        Files.writeString(input, sb.toString());
        return input;
    }

    private void assertStatus(UUID orderId, OrderStatus expected) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new AssertionError("Order not found: " + orderId));
        assertThat(order.getStatus())
                .as("Order %s should have status %s", orderId, expected)
                .isEqualTo(expected);
    }
}
