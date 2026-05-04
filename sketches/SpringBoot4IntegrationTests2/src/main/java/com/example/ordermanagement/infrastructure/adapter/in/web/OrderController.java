package com.example.ordermanagement.infrastructure.adapter.in.web;

import com.example.ordermanagement.domain.model.Order;
import com.example.ordermanagement.domain.port.in.CreateOrderUseCase;
import com.example.ordermanagement.domain.port.in.GetOrderUseCase;
import com.example.ordermanagement.domain.port.in.UpdateOrderStatusUseCase;
import com.example.ordermanagement.infrastructure.adapter.in.web.dto.CreateOrderRequest;
import com.example.ordermanagement.infrastructure.adapter.in.web.dto.OrderResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {

    private final CreateOrderUseCase createOrderUseCase;
    private final GetOrderUseCase getOrderUseCase;
    private final UpdateOrderStatusUseCase updateOrderStatusUseCase;

    public OrderController(CreateOrderUseCase createOrderUseCase,
                           GetOrderUseCase getOrderUseCase,
                           UpdateOrderStatusUseCase updateOrderStatusUseCase) {
        this.createOrderUseCase = createOrderUseCase;
        this.getOrderUseCase = getOrderUseCase;
        this.updateOrderStatusUseCase = updateOrderStatusUseCase;
    }

    @PostMapping
    public ResponseEntity<OrderResponse> createOrder(
            @Valid @RequestBody CreateOrderRequest request,
            @AuthenticationPrincipal Jwt jwt) {

        var command = new CreateOrderUseCase.CreateOrderCommand(
                jwt.getSubject(),
                request.items().stream()
                        .map(i -> new CreateOrderUseCase.CreateOrderCommand.OrderItemDto(
                                i.productId(), i.productName(), i.quantity()))
                        .toList()
        );

        Order order = createOrderUseCase.createOrder(command);
        return ResponseEntity.status(HttpStatus.CREATED).body(OrderResponse.from(order));
    }

    @GetMapping("/{orderId}")
    public ResponseEntity<OrderResponse> getOrder(@PathVariable UUID orderId) {
        return getOrderUseCase.getOrderById(orderId)
                .map(order -> ResponseEntity.ok(OrderResponse.from(order)))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping
    public ResponseEntity<List<OrderResponse>> getMyOrders(@AuthenticationPrincipal Jwt jwt) {
        List<OrderResponse> orders = getOrderUseCase.getOrdersByCustomer(jwt.getSubject())
                .stream().map(OrderResponse::from).toList();
        return ResponseEntity.ok(orders);
    }

    @PatchMapping("/{orderId}/confirm")
    public ResponseEntity<OrderResponse> confirmOrder(@PathVariable UUID orderId) {
        return ResponseEntity.ok(OrderResponse.from(updateOrderStatusUseCase.confirmOrder(orderId)));
    }

    @PatchMapping("/{orderId}/cancel")
    public ResponseEntity<OrderResponse> cancelOrder(@PathVariable UUID orderId) {
        return ResponseEntity.ok(OrderResponse.from(updateOrderStatusUseCase.cancelOrder(orderId)));
    }
}
