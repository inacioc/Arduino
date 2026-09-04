package com.example.ordermanagement.adapter.out.batch;

import com.example.ordermanagement.infrastructure.adapter.events.OrderConfirmationRequest;
import com.example.ordermanagement.infrastructure.adapter.events.OrderConfirmationRequestRepository;
import com.example.ordermanagement.infrastructure.adapter.out.batch.OrderConfirmationRequestPoller;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OrderConfirmationRequestPollerTest {

    private final OrderConfirmationRequestRepository repository = mock(OrderConfirmationRequestRepository.class);

    @Test
    void writesOneRowPerPendingRequestAndMarksThemProcessed(@TempDir Path outputDir) throws IOException {
        OrderConfirmationRequest first  = new OrderConfirmationRequest(UUID.randomUUID(), "CONFIRMED");
        OrderConfirmationRequest second = new OrderConfirmationRequest(UUID.randomUUID(), "CONFIRMED");
        when(repository.findAllByProcessedFalse()).thenReturn(List.of(first, second));

        new OrderConfirmationRequestPoller(repository, outputDir.toString()).poll();

        List<Path> written = Files.list(outputDir).toList();
        assertThat(written).hasSize(1);

        List<String> lines = Files.readAllLines(written.get(0));
        assertThat(lines.get(0)).isEqualTo("orderId,targetStatus");
        assertThat(lines).contains(
                first.getOrderId() + ",CONFIRMED",
                second.getOrderId() + ",CONFIRMED");

        assertThat(first.isProcessed()).isTrue();
        assertThat(second.isProcessed()).isTrue();
        verify(repository).saveAll(anyList());
    }

    @Test
    void doesNothingWhenNoRequestsArePending(@TempDir Path outputDir) throws IOException {
        when(repository.findAllByProcessedFalse()).thenReturn(List.of());

        new OrderConfirmationRequestPoller(repository, outputDir.toString()).poll();

        assertThat(Files.list(outputDir).toList()).isEmpty();
        verify(repository, never()).saveAll(anyList());
    }
}
