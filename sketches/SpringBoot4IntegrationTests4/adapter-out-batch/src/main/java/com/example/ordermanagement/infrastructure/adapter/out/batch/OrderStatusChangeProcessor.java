package com.example.ordermanagement.infrastructure.adapter.out.batch;

import com.example.ordermanagement.domain.port.in.ChangeOrderStatusUseCase;
import com.example.ordermanagement.domain.port.in.ChangeOrderStatusUseCase.ChangeOrderStatusResult;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Translates a single input row into an outcome row.
 * <p>
 * As a driving (primary) adapter it enters the hexagon through the
 * {@link ChangeOrderStatusUseCase} inbound port — it never touches an outbound port
 * directly, and holds no load/save orchestration. Its only job is transport mapping:
 * parse the id (a format concern), delegate the status change, and map the use case's
 * {@link ChangeOrderStatusResult.Outcome} to a CSV result code.
 * <p>
 * It deliberately never throws: every failure becomes an {@link OrderStatusChangeResult}
 * code so the job processes every line and the chunk transaction is not tainted.
 */
@Component
public class OrderStatusChangeProcessor
        implements ItemProcessor<OrderStatusChangeRequest, OrderStatusChangeResult> {

    private final ChangeOrderStatusUseCase changeOrderStatus;

    public OrderStatusChangeProcessor(ChangeOrderStatusUseCase changeOrderStatus) {
        this.changeOrderStatus = changeOrderStatus;
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

        ChangeOrderStatusResult outcome =
                changeOrderStatus.changeStatus(orderId, request.targetStatus());

        String presentStatus = outcome.status() == null ? "" : outcome.status();
        String code = switch (outcome.outcome()) {
            case CHANGED               -> OrderStatusChangeResult.OK;
            case ORDER_NOT_FOUND       -> OrderStatusChangeResult.ORDER_NOT_FOUND;
            case INVALID_TARGET_STATUS -> OrderStatusChangeResult.INVALID_TARGET_STATUS;
            case INVALID_TRANSITION    -> OrderStatusChangeResult.INVALID_TRANSITION;
        };
        return new OrderStatusChangeResult(orderId.toString(), presentStatus, code);
    }
}
