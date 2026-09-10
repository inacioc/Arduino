package com.example.ordermanagement.frontend.client.dto;

import java.util.List;

/** Mirrors adapter-in-web's {@code ResponseApiError} response shape — see {@link ApiErrorDto}. */
public record ResponseApiErrorDto(
        String status,
        List<ApiErrorDto> errors
) {}
