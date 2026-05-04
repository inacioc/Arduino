package com.example.ordermanagement.domain.port.out;

import com.example.ordermanagement.domain.model.Order;

import java.math.BigDecimal;

public interface PricingServicePort {

    BigDecimal calculateTotalPrice(Order order);
}
