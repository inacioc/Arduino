package com.example.ordermanagement.adapter.out.batch;

import com.example.ordermanagement.domain.event.OrderCreatedIntegrationEvent;
import com.example.ordermanagement.infrastructure.adapter.out.batch.OrderConfirmationRequestPoller;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.modulith.events.core.EventPublicationRegistry;
import org.springframework.modulith.events.core.PublicationTargetIdentifier;
import org.springframework.modulith.events.core.TargetEventPublication;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OrderConfirmationRequestPollerTest {

    private final EventPublicationRegistry registry = mock(EventPublicationRegistry.class);

    @Test
    void writesOneRowPerPendingPublicationAndMarksThemCompleted(@TempDir Path outputDir) throws IOException {
        OrderCreatedIntegrationEvent first  = new OrderCreatedIntegrationEvent(UUID.randomUUID(), "cust-1", LocalDateTime.now());
        OrderCreatedIntegrationEvent second = new OrderCreatedIntegrationEvent(UUID.randomUUID(), "cust-2", LocalDateTime.now());
        TargetEventPublication firstPub  = TargetEventPublication.of(first, PublicationTargetIdentifier.of("listener-1"));
        TargetEventPublication secondPub = TargetEventPublication.of(second, PublicationTargetIdentifier.of("listener-1"));
        when(registry.findIncompletePublications()).thenReturn(List.of(firstPub, secondPub));

        new OrderConfirmationRequestPoller(registry, outputDir.toString()).poll();

        List<Path> written = Files.list(outputDir).toList();
        assertThat(written).hasSize(1);

        List<String> lines = Files.readAllLines(written.get(0));
        assertThat(lines.get(0)).isEqualTo("orderId,targetStatus");
        assertThat(lines).contains(
                first.orderId() + ",CONFIRMED",
                second.orderId() + ",CONFIRMED");

        verify(registry).markCompleted(first, firstPub.getTargetIdentifier());
        verify(registry).markCompleted(second, secondPub.getTargetIdentifier());
    }

    @Test
    void ignoresPublicationsOfOtherEventTypes(@TempDir Path outputDir) throws IOException {
        TargetEventPublication other = TargetEventPublication.of("not-an-order-event",
                PublicationTargetIdentifier.of("listener-1"));
        when(registry.findIncompletePublications()).thenReturn(List.of(other));

        new OrderConfirmationRequestPoller(registry, outputDir.toString()).poll();

        assertThat(Files.list(outputDir).toList()).isEmpty();
        verify(registry, never()).markCompleted(any(), any());
    }

    @Test
    void doesNothingWhenNoPublicationsArePending(@TempDir Path outputDir) throws IOException {
        when(registry.findIncompletePublications()).thenReturn(List.of());

        new OrderConfirmationRequestPoller(registry, outputDir.toString()).poll();

        assertThat(Files.list(outputDir).toList()).isEmpty();
        verify(registry, never()).markCompleted(any(), any());
    }
}
