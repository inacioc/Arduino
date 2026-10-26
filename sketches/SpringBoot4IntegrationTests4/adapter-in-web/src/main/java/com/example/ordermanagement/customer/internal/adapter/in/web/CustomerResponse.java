package com.example.ordermanagement.customer.internal.adapter.in.web;

import com.example.ordermanagement.customer.Customer;

import java.util.UUID;

public record CustomerResponse(
        UUID id,
        String firstName,
        String lastName,
        String telephone,
        String email
) {
    public static CustomerResponse from(Customer customer) {
        return new CustomerResponse(
                customer.getId(),
                customer.getFirstName(),
                customer.getLastName(),
                customer.getTelephone(),
                customer.getEmail()
        );
    }
}
