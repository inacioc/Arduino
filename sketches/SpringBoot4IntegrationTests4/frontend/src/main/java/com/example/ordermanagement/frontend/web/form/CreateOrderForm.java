package com.example.ordermanagement.frontend.web.form;

import com.example.ordermanagement.frontend.web.validation.UniqueProducts;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.ArrayList;
import java.util.List;

@UniqueProducts
public class CreateOrderForm {

    @NotBlank(message = "Customer id is required")
    private String customerId;

    @NotEmpty(message = "Add at least one item")
    private List<@Valid OrderItemForm> items = new ArrayList<>();

    public String getCustomerId() {
        return customerId;
    }

    public void setCustomerId(String customerId) {
        this.customerId = customerId;
    }

    public List<OrderItemForm> getItems() {
        return items;
    }

    public void setItems(List<OrderItemForm> items) {
        this.items = items;
    }
}
