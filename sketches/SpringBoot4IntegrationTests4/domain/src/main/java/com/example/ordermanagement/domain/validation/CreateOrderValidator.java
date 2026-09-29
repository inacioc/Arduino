package com.example.ordermanagement.domain.validation;

import com.example.ordermanagement.domain.model.Product;
import com.example.ordermanagement.domain.port.in.CreateOrderUseCase.CreateOrderCommand;
import com.example.ordermanagement.domain.port.in.CreateOrderUseCase.OrderItemCommand;
import com.example.ordermanagement.domain.port.out.CustomerRepositoryPort;
import com.example.ordermanagement.domain.port.out.ProductRepositoryPort;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Functional rules for placing an order: the customer must exist, and every line must
 * reference an existing, orderable product at its current catalogue price. Each bad line
 * is reported under the product id; a missing customer is reported under "customerId" -
 * both accumulate into the same notification (see {@code Validation.md}), so a request with
 * both problems gets one 422 listing all of them, not a first-error-wins 404/422 mix.
 */
@Component
public class CreateOrderValidator implements DomainValidator<CreateOrderCommand> {

    public static final String ITEMS_REQUIRED = "ITEMS_REQUIRED";
    public static final String CUSTOMER_NOT_FOUND = "CUSTOMER_NOT_FOUND";
    public static final String PRODUCT_NOT_FOUND = "PRODUCT_NOT_FOUND";
    public static final String PRODUCT_NOT_AVAILABLE = "PRODUCT_NOT_AVAILABLE";
    public static final String PRICE_MISMATCH = "PRICE_MISMATCH";

    private final CustomerRepositoryPort customerRepository;
    private final ProductRepositoryPort productRepository;

    public CreateOrderValidator(CustomerRepositoryPort customerRepository,
                                 ProductRepositoryPort productRepository) {
        this.customerRepository = customerRepository;
        this.productRepository = productRepository;
    }

    @Override
    public void validate(CreateOrderCommand command, ValidationNotification notification) {
        if (command.customerId() == null || customerRepository.findById(command.customerId()).isEmpty()) {
            String path = command.customerId() == null ? "customerId" : command.customerId().toString();
            notification.addError(CUSTOMER_NOT_FOUND, path, "Customer not found: " + path);
        }

        if (command.items() == null || command.items().isEmpty()) {
            notification.addError(ITEMS_REQUIRED, "items", "An order must have at least one item.");
            return;
        }

        for (OrderItemCommand line : command.items()) {
            String path = line.productId().toString();

            Optional<Product> found = productRepository.findById(line.productId());
            if (found.isEmpty()) {
                notification.addError(PRODUCT_NOT_FOUND, path, "Product not found: " + path);
                continue;
            }

            Product product = found.get();
            if (!product.isOrderable()) {
                notification.addError(PRODUCT_NOT_AVAILABLE, path, "Product not available: " + path);
            } else if (product.getPrice().compareTo(line.unitPrice()) != 0) {
                // The submitted price is never trusted as the source of truth for money: it must
                // match the catalogue, which catches stale client prices and tampering.
                notification.addError(PRICE_MISMATCH, path,
                        "Price mismatch for product %s: submitted %s, current price %s"
                                .formatted(path, line.unitPrice(), product.getPrice()));
            }
        }
    }
}
