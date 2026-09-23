package com.example.ordermanagement.domain.validation;

import com.example.ordermanagement.domain.exception.ProductAlreadyExistsException;
import com.example.ordermanagement.domain.model.Product;
import com.example.ordermanagement.domain.port.in.SaveProductUseCase.SaveProductCommand;
import com.example.ordermanagement.domain.port.out.ProductRepositoryPort;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * Functional rules for saving a product: the mandatory fields must be present, and the
 * catalogue name must stay unique.
 * <p>
 * Mandatory-field gaps are reported through {@link ValidationNotification} like any other
 * rule (defense in depth: {@code CreateProductRequest}'s Bean Validation already covers
 * this for the web adapter, but a future adapter without it — batch, messaging — must not
 * be able to construct an invalid {@link Product}). The name-uniqueness check is different:
 * it's reported the same way the database's own unique constraint already is —
 * {@link ProductAlreadyExistsException}, 409 — rather than through the notification, so the
 * response is identical whether this fast pre-check catches the duplicate or the
 * {@code uk_products_name} constraint does (e.g. a race between two concurrent requests
 * for the same name; the constraint remains the source of truth, this just avoids the
 * wasted round trip in the common case). See {@code Validation.md}'s "known gap" note.
 */
@Component
public class SaveProductValidator implements DomainValidator<SaveProductCommand> {

    public static final String ID_REQUIRED = "ID_REQUIRED";
    public static final String NAME_REQUIRED = "NAME_REQUIRED";
    public static final String PRICE_INVALID = "PRICE_INVALID";

    private final ProductRepositoryPort productRepository;

    public SaveProductValidator(ProductRepositoryPort productRepository) {
        this.productRepository = productRepository;
    }

    @Override
    public void validate(SaveProductCommand command, ValidationNotification notification) {
        boolean missingMandatoryField = false;

        if (command.id() == null) {
            notification.addError(ID_REQUIRED, "id", "Product id is required.");
            missingMandatoryField = true;
        }
        if (command.name() == null || command.name().isBlank()) {
            notification.addError(NAME_REQUIRED, "name", "Product name must not be blank.");
            missingMandatoryField = true;
        }
        if (command.price() == null || command.price().compareTo(BigDecimal.ZERO) <= 0) {
            notification.addError(PRICE_INVALID, "price", "Product price must be positive.");
            missingMandatoryField = true;
        }
        // The uniqueness check needs a usable id/name to compare against - skip it rather
        // than query the repository with a null/blank name or report a misleading duplicate.
        if (missingMandatoryField) {
            return;
        }

        Optional<Product> existing = productRepository.findByName(command.name());
        if (existing.isPresent() && !existing.get().getId().equals(command.id())) {
            throw new ProductAlreadyExistsException(command.name());
        }
    }
}
