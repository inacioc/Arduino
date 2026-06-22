package com.example.ordermanagement.infrastructure.adapter.out.persistence;

import com.example.ordermanagement.domain.model.Order;
import com.example.ordermanagement.domain.model.OrderItem;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface OrderEntityMapper {

    // ── Domain → Entity ───────────────────────────────────────────────────────

    @Mapping(target = "items", ignore = true) // handled manually in adapter
    OrderEntity toEntity(Order order);

    @Mapping(target = "order", ignore = true)
    OrderItemEntity toItemEntity(OrderItem item);

    // ── Entity → Domain ───────────────────────────────────────────────────────

    default Order toDomain(OrderEntity entity) {
        List<OrderItem> items = entity.getItems().stream()
                .map(this::toItemDomain)
                .toList();

        return Order.reconstitute(
                entity.getId(),
                entity.getCustomerId(),
                items,
                entity.getStatus(),
                entity.getTotalAmount(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    default OrderItem toItemDomain(OrderItemEntity entity) {
        return new OrderItem(
                entity.getProductId(),
                entity.getProductName(),
                entity.getQuantity(),
                entity.getUnitPrice()
        );
    }
}
