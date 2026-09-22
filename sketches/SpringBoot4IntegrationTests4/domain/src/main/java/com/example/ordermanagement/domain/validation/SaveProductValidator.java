package com.example.ordermanagement.domain.validation;

import com.example.ordermanagement.domain.exception.ProductAlreadyExistsException;
import com.example.ordermanagement.domain.model.Product;
import com.example.ordermanagement.domain.port.in.SaveProductUseCase.SaveProductCommand;
import com.example.ordermanagement.domain.port.out.ProductRepositoryPort;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Functional rules for saving a product: the catalogue name must stay unique.
 * <p>
 * Reported the same way the database's own unique constraint already is —
 * {@link ProductAlreadyExistsException}, 409 — rather than through {@link ValidationNotification},
 * so the response is identical whether this fast pre-check catches the duplicate or the
 * {@code uk_products_name} constraint does (e.g. a race between two concurrent requests
 * for the same name; the constraint remains the source of truth, this just avoids the
 * wasted round trip in the common case). See {@code Validation.md}'s "known gap" note.
 */
@Component
public class SaveProductValidator implements DomainValidator<SaveProductCommand> {

    private final ProductRepositoryPort productRepository;

    public SaveProductValidator(ProductRepositoryPort productRepository) {
        this.productRepository = productRepository;
    }

    @Override
    public void validate(SaveProductCommand command, ValidationNotification notification) {
        Optional<Product> existing = productRepository.findByName(command.name());
        if (existing.isPresent() && !existing.get().getId().equals(command.id())) {
            throw new ProductAlreadyExistsException(command.name());
        }
    }
}
