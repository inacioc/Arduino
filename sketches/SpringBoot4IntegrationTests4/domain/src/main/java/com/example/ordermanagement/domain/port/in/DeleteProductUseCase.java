package com.example.ordermanagement.domain.port.in;

import java.util.UUID;

public interface DeleteProductUseCase {

    void deleteById(UUID productId);
}
