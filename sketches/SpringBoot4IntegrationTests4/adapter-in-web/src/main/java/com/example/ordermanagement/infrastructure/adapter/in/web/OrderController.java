package com.example.ordermanagement.infrastructure.adapter.in.web;

import com.example.ordermanagement.domain.port.in.CreateOrderUseCase;
import com.example.ordermanagement.domain.port.in.CreateOrderUseCase.CreateOrderCommand;
import com.example.ordermanagement.domain.port.in.CreateOrderUseCase.OrderItemCommand;
import com.example.ordermanagement.domain.port.in.GetOrderUseCase;
import com.example.ordermanagement.domain.port.in.ProcessOrderUseCase;
import com.example.ordermanagement.infrastructure.adapter.in.web.dto.CreateOrderRequest;
import com.example.ordermanagement.infrastructure.adapter.in.web.dto.OrderResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final CreateOrderUseCase createOrder;
    private final GetOrderUseCase getOrder;
    private final ProcessOrderUseCase processOrder;

    public OrderController(CreateOrderUseCase createOrder,
                           GetOrderUseCase getOrder,
                           ProcessOrderUseCase processOrder) {
        this.createOrder  = createOrder;
        this.getOrder     = getOrder;
        this.processOrder = processOrder;
    }

    @PostMapping
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<OrderResponse> create(@Valid @RequestBody CreateOrderRequest request) {
        CreateOrderCommand command = new CreateOrderCommand(
                request.customerId(),
                request.items().stream()
                        .map(i -> new OrderItemCommand(i.productId(), i.quantity(), i.unitPrice()))
                        .toList()
        );
        OrderResponse body = OrderResponse.from(createOrder.createOrder(command));
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'ADMIN')")
    public ResponseEntity<OrderResponse> getById(@PathVariable UUID id) {
        return getOrder.findById(id)
                .map(OrderResponse::from)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public List<OrderResponse> getByStatus(
            @RequestParam(required = false, defaultValue = "PENDING") String status) {
        return getOrder.findByStatus(status).stream()
                .map(OrderResponse::from)
                .toList();
    }

    @GetMapping("/customer/{customerId}")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'ADMIN')")
    public List<OrderResponse> getByCustomer(@PathVariable String customerId) {
        return getOrder.findByCustomerId(customerId).stream()
                .map(OrderResponse::from)
                .toList();
    }

    @PutMapping("/{id}/confirm")
    @PreAuthorize("hasRole('ADMIN')")
    public OrderResponse confirm(@PathVariable UUID id) {
        return OrderResponse.from(processOrder.confirmOrder(id));
    }

    @PutMapping("/{id}/complete")
    @PreAuthorize("hasRole('ADMIN')")
    public OrderResponse complete(@PathVariable UUID id) {
        return OrderResponse.from(processOrder.completeOrder(id));
    }

    @PutMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'ADMIN')")
    public OrderResponse cancel(@PathVariable UUID id) {
        return OrderResponse.from(processOrder.cancelOrder(id));
    }
}
