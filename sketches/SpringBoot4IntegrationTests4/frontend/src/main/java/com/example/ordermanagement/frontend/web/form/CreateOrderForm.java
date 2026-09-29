package com.example.ordermanagement.frontend.web.form;

import com.example.ordermanagement.frontend.web.validation.UniqueProducts;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@UniqueProducts
public class CreateOrderForm {

    @NotNull(message = "Customer id is required")
    private UUID customerId;

    @NotEmpty(message = "Add at least one item")
    private List<@Valid OrderItemForm> items = new ArrayList<>();

    public UUID getCustomerId() {
        return customerId;
    }

    public void setCustomerId(UUID customerId) {
        this.customerId = customerId;
    }

    public List<OrderItemForm> getItems() {
        return items;
    }

    public void setItems(List<OrderItemForm> items) {
        this.items = items;
    }
}
