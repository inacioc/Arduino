package com.example.ordermanagement.domain.validation;

import com.example.ordermanagement.domain.exception.ProductAlreadyExistsException;
import com.example.ordermanagement.domain.model.Product;
import com.example.ordermanagement.domain.port.in.SaveProductUseCase.SaveProductCommand;
import com.example.ordermanagement.domain.port.out.ProductRepositoryPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SaveProductValidatorTest {

    private final InMemoryProductRepository productRepository = new InMemoryProductRepository();
    private final SaveProductValidator validator = new SaveProductValidator(productRepository);

    private static final UUID EXISTING = UUID.fromString("22222222-0000-0000-0000-000000000001");
    private static final UUID OTHER = UUID.fromString("22222222-0000-0000-0000-000000000002");

    @Test
    @DisplayName("a brand new name is valid")
    void newName_isValid() {
        assertThatCode(() -> validator.assertValid(new SaveProductCommand(
                OTHER, "Widget", new BigDecimal("9.99"), true))).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("re-saving the same product under its own name is valid (idempotent update)")
    void sameProductSameName_isValid() {
        productRepository.save(Product.create(EXISTING, "Widget", new BigDecimal("9.99"), true));

        assertThatCode(() -> validator.assertValid(new SaveProductCommand(
                EXISTING, "Widget", new BigDecimal("12.99"), false))).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("a different product already holds this name → ProductAlreadyExistsException, not a notification violation")
    void nameTakenByAnotherProduct_throwsProductAlreadyExists() {
        productRepository.save(Product.create(EXISTING, "Widget", new BigDecimal("9.99"), true));

        assertThatThrownBy(() -> validator.assertValid(new SaveProductCommand(
                OTHER, "Widget", new BigDecimal("9.99"), true)))
                .isInstanceOf(ProductAlreadyExistsException.class);
    }

    private static final class InMemoryProductRepository implements ProductRepositoryPort {
        private final Map<UUID, Product> store = new HashMap<>();

        @Override public Product save(Product product) { store.put(product.getId(), product); return product; }
        @Override public Optional<Product> findById(UUID id) { return Optional.ofNullable(store.get(id)); }
        @Override public Optional<Product> findByName(String name) {
            return store.values().stream().filter(p -> p.getName().equals(name)).findFirst();
        }
        @Override public List<Product> findAll() { return List.copyOf(store.values()); }
        @Override public void deleteById(UUID id) { store.remove(id); }
    }
}
