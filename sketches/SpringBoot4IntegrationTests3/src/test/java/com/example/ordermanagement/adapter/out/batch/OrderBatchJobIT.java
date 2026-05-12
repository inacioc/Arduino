package com.example.ordermanagement.adapter.out.batch;

import com.example.ordermanagement.domain.model.Order;
import com.example.ordermanagement.domain.model.OrderItem;
import com.example.ordermanagement.domain.model.OrderStatus;
import com.example.ordermanagement.domain.port.out.OrderRepositoryPort;
import com.example.ordermanagement.infrastructure.adapter.out.batch.OrderBatchConfig;
import com.example.ordermanagement.support.IntegrationTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.*;
import org.springframework.batch.test.JobLauncherTestUtils;
import org.springframework.batch.test.JobRepositoryTestUtils;
import org.springframework.batch.test.context.SpringBatchTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.jdbc.Sql;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for the Spring Batch job that processes CONFIRMED orders.
 *
 * Strategy:
 *  - @SpringBatchTest provides:
 *      JobLauncherTestUtils  — launches jobs in test context
 *      JobRepositoryTestUtils — cleans up batch metadata between tests
 *  - Real PostgreSQL for both domain data and batch metadata tables.
 *  - @Sql scripts set up domain data (orders) before each test.
 *
 * Spring Batch 5 notes:
 *  - No @EnableBatchProcessing (Spring Boot auto-configuration handles it)
 *  - JobBuilder / StepBuilder API instead of deprecated *Factory classes
 *  - Batch schema auto-created (spring.batch.jdbc.initialize-schema=always)
 */
@SpringBatchTest
@Sql(scripts = "/sql/clean-orders.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class OrderBatchJobIT extends IntegrationTestBase {

    @Autowired
    private JobLauncherTestUtils jobLauncherTestUtils;

    @Autowired
    private JobRepositoryTestUtils jobRepositoryTestUtils;

    @Autowired
    private OrderRepositoryPort orderRepository;

    // ── Happy path: process CONFIRMED orders ──────────────────────────────────

    @Test
    @DisplayName("Job completes successfully and moves CONFIRMED orders to COMPLETED")
    void job_processesConfirmedOrders() throws Exception {
        // Arrange: create 3 CONFIRMED orders in DB
        Order c1 = saveConfirmedOrder("batch-customer-1");
        Order c2 = saveConfirmedOrder("batch-customer-2");
        Order c3 = saveConfirmedOrder("batch-customer-3");

        // Act: launch the job
        JobExecution execution = jobLauncherTestUtils.launchJob();

        // Assert job status
        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(execution.getExitStatus().getExitCode()).isEqualTo(ExitStatus.COMPLETED.getExitCode());

        // Assert domain state: all 3 orders are now COMPLETED
        assertOrderStatus(c1.getId(), OrderStatus.COMPLETED);
        assertOrderStatus(c2.getId(), OrderStatus.COMPLETED);
        assertOrderStatus(c3.getId(), OrderStatus.COMPLETED);
    }

    @Test
    @DisplayName("Job step reads correct count of CONFIRMED orders")
    void job_stepExecutionHasCorrectReadWriteCount() throws Exception {
        saveConfirmedOrder("batch-cust-a");
        saveConfirmedOrder("batch-cust-b");

        JobExecution execution = jobLauncherTestUtils.launchJob();

        StepExecution stepExecution = execution.getStepExecutions().iterator().next();
        assertThat(stepExecution.getReadCount()).isEqualTo(2);
        assertThat(stepExecution.getWriteCount()).isEqualTo(2);
        assertThat(stepExecution.getSkipCount()).isZero();
    }

    @Test
    @DisplayName("Job completes with 0 reads when no CONFIRMED orders exist")
    void job_noConfirmedOrders_completesWithZeroReads() throws Exception {
        // Only PENDING order in DB — should not be picked up
        Order pending = Order.create("pending-customer",
                List.of(new OrderItem("P-1", "Product", 1, BigDecimal.TEN)));
        orderRepository.save(pending);

        JobExecution execution = jobLauncherTestUtils.launchJob();

        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        StepExecution step = execution.getStepExecutions().iterator().next();
        assertThat(step.getReadCount()).isZero();
    }

    // ── Step-level test (tests step in isolation) ─────────────────────────────

    @Test
    @DisplayName("Step 'processOrdersStep' can be tested in isolation via launchStep()")
    void processOrdersStep_isolation() throws Exception {
        saveConfirmedOrder("step-isolation-customer");

        JobExecution execution = jobLauncherTestUtils.launchStep(OrderBatchConfig.STEP_NAME);

        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Order saveConfirmedOrder(String customerId) {
        Order order = Order.create(customerId, List.of(
                new OrderItem("PROD-BATCH", "Batch Test Product", 1, new BigDecimal("100.00"))
        ));
        order.confirm();
        return orderRepository.save(order);
    }

    private void assertOrderStatus(java.util.UUID orderId, OrderStatus expectedStatus) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new AssertionError("Order not found: " + orderId));
        assertThat(order.getStatus())
                .as("Order %s should have status %s", orderId, expectedStatus)
                .isEqualTo(expectedStatus);
    }
}
