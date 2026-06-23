package com.example.ordermanagement.domain.port.in;

import com.example.ordermanagement.domain.model.Order;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Read-only result exposed by the inbound order use cases.
 * <p>
 * In a strict hexagonal design the use-case contract speaks only in its own
 * DTOs, so the rich {@link Order} aggregate (and its mutating behaviour) never
 * leaks past the port. {@code status} is a plain {@code String} so even the
 * {@code OrderStatus} domain enum stays inside the hexagon.
 */
public record OrderResult(
        UUID id,
        String customerId,
        String status,
        BigDecimal totalAmount,
        List<OrderItemResult> items,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public record OrderItemResult(
            String productId,
            String productName,
            int quantity,
            BigDecimal unitPrice,
            BigDecimal subtotal
    ) {}

    /** Maps the domain aggregate to a result DTO. The only place {@code Order} is read at the boundary. */
    public static OrderResult from(Order order) {
        List<OrderItemResult> items = order.getItems().stream()
                .map(item -> new OrderItemResult(
                        item.getProductId(),
                        item.getProductName(),
                        item.getQuantity(),
                        item.getUnitPrice(),
                        item.subtotal()
                ))
                .toList();

        return new OrderResult(
                order.getId(),
                order.getCustomerId(),
                order.getStatus().name(),
                order.getTotalAmount(),
                items,
                order.getCreatedAt(),
                order.getUpdatedAt()
        );
    }
}
