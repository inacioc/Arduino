package com.example.orders.infrastructure.in.web;

import com.example.orders.domain.model.Money;
import com.example.orders.domain.model.OrderItem;
import com.example.orders.domain.port.in.CreateOrderCommand;
import com.example.orders.domain.port.in.CreateOrderUseCase;
import com.example.orders.domain.port.in.GetOrderUseCase;
import com.example.orders.domain.service.InsufficientInventoryException;
import com.example.orders.infrastructure.in.web.dto.CreateOrderRequest;
import com.example.orders.infrastructure.in.web.dto.OrderResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final CreateOrderUseCase createOrderUseCase;
    private final GetOrderUseCase getOrderUseCase;

    public OrderController(CreateOrderUseCase createOrderUseCase, GetOrderUseCase getOrderUseCase) {
        this.createOrderUseCase = createOrderUseCase;
        this.getOrderUseCase = getOrderUseCase;
    }

    @PostMapping
    public ResponseEntity<OrderResponse> createOrder(@Valid @RequestBody CreateOrderRequest request) {
        List<OrderItem> items = request.items().stream()
                .map(i -> new OrderItem(
                        i.productId(),
                        i.productName(),
                        i.quantity(),
                        Money.of(i.unitPrice(), i.currency())
                ))
                .toList();

        var command = new CreateOrderCommand(request.customerId(), items);
        var order = createOrderUseCase.createOrder(command);
        var response = OrderResponse.from(order);

        return ResponseEntity
                .created(URI.create("/api/orders/" + order.getId()))
                .body(response);
    }

    @GetMapping("/{orderId}")
    public ResponseEntity<OrderResponse> getOrder(@PathVariable String orderId) {
        return getOrderUseCase.getOrder(orderId)
                .map(OrderResponse::from)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @ExceptionHandler(InsufficientInventoryException.class)
    public ResponseEntity<ProblemDetail> handleInsufficientInventory(InsufficientInventoryException ex) {
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage());
        problem.setTitle("Insufficient Inventory");
        problem.setType(URI.create("/errors/insufficient-inventory"));
        return ResponseEntity.unprocessableEntity().body(problem);
    }
}
