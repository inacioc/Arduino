package com.example.ordermanagement.order.internal.adapter.in.web;

import com.example.ordermanagement.order.OrderNotFoundException;
import com.example.ordermanagement.order.internal.domain.Order;
import com.example.ordermanagement.order.internal.domain.OrderStatus;
import com.example.ordermanagement.order.CreateOrderUseCase;
import com.example.ordermanagement.order.CreateOrderUseCase.CreateOrderCommand;
import com.example.ordermanagement.order.CreateOrderUseCase.OrderItemCommand;
import com.example.ordermanagement.order.DeleteOrderUseCase;
import com.example.ordermanagement.order.GetOrderUseCase;
import com.example.ordermanagement.order.ProcessOrderUseCase;
import com.example.ordermanagement.order.internal.adapter.in.web.CreateOrderRequest;
import com.example.ordermanagement.order.internal.adapter.in.web.OrderResponse;
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
    private final DeleteOrderUseCase deleteOrder;

    public OrderController(CreateOrderUseCase createOrder,
                           GetOrderUseCase getOrder,
                           ProcessOrderUseCase processOrder,
                           DeleteOrderUseCase deleteOrder) {
        this.createOrder  = createOrder;
        this.getOrder     = getOrder;
        this.processOrder = processOrder;
        this.deleteOrder  = deleteOrder;
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
        Order order = createOrder.createOrder(command);
        return ResponseEntity.status(HttpStatus.CREATED).body(OrderResponse.from(order));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'ADMIN')")
    public OrderResponse getById(@PathVariable UUID id) {
        return getOrder.findById(id)
                .map(OrderResponse::from)
                // Thrown rather than a bare ResponseEntity.notFound(), so the 404 goes
                // through GlobalExceptionHandler and comes back as a ResponseApiError like
                // every other error this API returns, instead of an empty body.
                .orElseThrow(() -> new OrderNotFoundException(id.toString()));
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public List<OrderResponse> getByStatus(
            @RequestParam(required = false) OrderStatus status) {
        List<Order> orders = status != null
                ? getOrder.findByStatus(status)
                : getOrder.findByStatus(OrderStatus.PENDING);
        return orders.stream().map(OrderResponse::from).toList();
    }

    @GetMapping("/customer/{customerId}")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'ADMIN')")
    public List<OrderResponse> getByCustomer(@PathVariable UUID customerId) {
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

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        deleteOrder.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
