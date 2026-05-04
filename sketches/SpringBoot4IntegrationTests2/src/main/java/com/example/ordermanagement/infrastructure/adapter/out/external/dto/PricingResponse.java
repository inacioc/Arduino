package com.example.ordermanagement.infrastructure.adapter.out.external.dto;

import java.math.BigDecimal;

public record PricingResponse(BigDecimal totalPrice, String currency) {}
