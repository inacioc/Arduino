package com.example.ordermanagement.frontend.web.validation;

import com.example.ordermanagement.frontend.web.form.CreateOrderForm;
import com.example.ordermanagement.frontend.web.form.OrderItemForm;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class UniqueProductsValidator implements ConstraintValidator<UniqueProducts, CreateOrderForm> {

    @Override
    public boolean isValid(CreateOrderForm form, ConstraintValidatorContext context) {
        if (form.getItems() == null) {
            return true;
        }
        Set<UUID> seen = new HashSet<>();
        for (OrderItemForm item : form.getItems()) {
            if (item.getProductId() != null && !seen.add(item.getProductId())) {
                return false;
            }
        }
        return true;
    }
}
