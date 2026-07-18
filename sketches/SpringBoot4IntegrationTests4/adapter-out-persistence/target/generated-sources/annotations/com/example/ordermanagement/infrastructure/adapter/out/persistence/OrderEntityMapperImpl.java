package com.example.ordermanagement.infrastructure.adapter.out.persistence;

import com.example.ordermanagement.domain.model.Order;
import com.example.ordermanagement.domain.model.OrderItem;
import javax.annotation.processing.Generated;
import org.springframework.stereotype.Component;

@Generated(
    value = "org.mapstruct.ap.MappingProcessor",
    date = "2026-07-06T08:58:40+0200",
    comments = "version: 1.5.5.Final, compiler: Eclipse JDT (IDE) 3.46.100.v20260624-0231, environment: Java 21.0.11 (Eclipse Adoptium)"
)
@Component
public class OrderEntityMapperImpl implements OrderEntityMapper {

    @Override
    public OrderEntity toEntity(Order order) {
        if ( order == null ) {
            return null;
        }

        OrderEntity orderEntity = new OrderEntity();

        orderEntity.setCreatedAt( order.getCreatedAt() );
        orderEntity.setCustomerId( order.getCustomerId() );
        orderEntity.setId( order.getId() );
        orderEntity.setStatus( order.getStatus() );
        orderEntity.setTotalAmount( order.getTotalAmount() );
        orderEntity.setUpdatedAt( order.getUpdatedAt() );

        return orderEntity;
    }

    @Override
    public OrderItemEntity toItemEntity(OrderItem item) {
        if ( item == null ) {
            return null;
        }

        OrderItemEntity orderItemEntity = new OrderItemEntity();

        orderItemEntity.setProductId( item.getProductId() );
        orderItemEntity.setProductName( item.getProductName() );
        orderItemEntity.setQuantity( item.getQuantity() );
        orderItemEntity.setUnitPrice( item.getUnitPrice() );

        return orderItemEntity;
    }
}
