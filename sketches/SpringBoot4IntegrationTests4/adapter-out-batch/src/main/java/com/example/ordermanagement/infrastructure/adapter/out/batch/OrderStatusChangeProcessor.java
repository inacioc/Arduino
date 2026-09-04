package com.example.ordermanagement.infrastructure.adapter.out.batch;

import com.example.ordermanagement.domain.model.Order;
import com.example.ordermanagement.domain.model.OrderStatus;
import com.example.ordermanagement.domain.port.in.GetOrderUseCase;
import com.example.ordermanagement.domain.port.in.ProcessOrderUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.infrastructure.item.ItemProcessor;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * Attempts the status change for a single input row and produces the result row.
 * <p>
 * It deliberately never throws: every failure (bad id, unknown status, missing
 * order, illegal transition) is captured as an {@link OrderStatusChangeResult}
 * error code so the job processes every line and the chunk transaction is not
 * tainted by a per-row error.
 */
@Component
public class OrderStatusChangeProcessor
        implements ItemProcessor<OrderStatusChangeRequest, OrderStatusChangeResult> {

    private static final Logger log = LoggerFactory.getLogger(OrderStatusChangeProcessor.class);

    private final GetOrderUseCase getOrderUseCase;
    private final ProcessOrderUseCase processOrderUseCase;

    public OrderStatusChangeProcessor(GetOrderUseCase getOrderUseCase,
                                       ProcessOrderUseCase processOrderUseCase) {
        this.getOrderUseCase     = getOrderUseCase;
        this.processOrderUseCase = processOrderUseCase;
    }

    @Override
    public OrderStatusChangeResult process(OrderStatusChangeRequest request) {
        UUID orderId;
        try {
            orderId = UUID.fromString(request.orderId().trim());
        } catch (IllegalArgumentException e) {
            return new OrderStatusChangeResult(
                    request.orderId(), "", OrderStatusChangeResult.INVALID_ID);
        }

        OrderStatus target;
        try {
            target = OrderStatus.valueOf(request.targetStatus().trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return new OrderStatusChangeResult(
                    request.orderId(), "", OrderStatusChangeResult.INVALID_TARGET_STATUS);
        }

        Optional<Order> found = getOrderUseCase.findById(orderId);
        if (found.isEmpty()) {
            return new OrderStatusChangeResult(
                    request.orderId(), "", OrderStatusChangeResult.ORDER_NOT_FOUND);
        }

        String presentStatus = found.get().getStatus().name();
        try {
            Order updated = processOrderUseCase.changeStatus(orderId, target);
            return new OrderStatusChangeResult(
                    updated.getId().toString(), updated.getStatus().name(),
                    OrderStatusChangeResult.OK);
        } catch (IllegalStateException e) {
            log.debug("Illegal transition for order {} -> {}: {}",
                    orderId, target, e.getMessage());
            return new OrderStatusChangeResult(
                    orderId.toString(), presentStatus,
                    OrderStatusChangeResult.INVALID_TRANSITION);
        }
    }
}
