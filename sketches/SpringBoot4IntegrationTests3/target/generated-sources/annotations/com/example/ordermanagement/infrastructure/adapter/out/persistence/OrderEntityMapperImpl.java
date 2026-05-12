package com.example.ordermanagement.infrastructure.adapter.out.persistence;

import com.example.ordermanagement.domain.model.Order;
import com.example.ordermanagement.domain.model.OrderItem;
import javax.annotation.processing.Generated;
import org.springframework.stereotype.Component;

@Generated(
    value = "org.mapstruct.ap.MappingProcessor",
    date = "2026-05-11T07:10:31+0200",
    comments = "version: 1.5.5.Final, compiler: javac, environment: Java 17.0.6 (OpenLogic)"
)
@Component
public class OrderEntityMapperImpl implements OrderEntityMapper {

    @Override
    public OrderEntity toEntity(Order order) {
        if ( order == null ) {
            return null;
        }

        OrderEntity orderEntity = new OrderEntity();

        orderEntity.setId( order.getId() );
        orderEntity.setCustomerId( order.getCustomerId() );
        orderEntity.setStatus( order.getStatus() );
        orderEntity.setTotalAmount( order.getTotalAmount() );
        orderEntity.setCreatedAt( order.getCreatedAt() );
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
