package com.example.ordermanagement.adapter.out.persistence;

import com.example.ordermanagement.domain.model.Product;
import com.example.ordermanagement.domain.port.out.ProductRepositoryPort;
import com.example.ordermanagement.support.IntegrationTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for ProductPersistenceAdapter against a real PostgreSQL database.
 *
 * Mirrors {@link OrderPersistenceAdapterIT}: each test runs in a transaction that
 * rolls back automatically, and the products table is cleaned before each method.
 */
@Transactional
@Sql(scripts = "/sql/clean-products.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class ProductPersistenceAdapterIT extends IntegrationTestBase {

    @Autowired
    private ProductRepositoryPort productRepository;

    private static final UUID P_1 = UUID.fromString("66666666-0000-0000-0000-000000000001");
    private static final UUID P_2 = UUID.fromString("66666666-0000-0000-0000-000000000002");

    // ── Save ──────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("save() persists a new product and returns it unchanged")
    void save_persistsNewProduct() {
        Product saved = productRepository.save(
                Product.create(P_1, "Widget Alpha", new BigDecimal("49.99"), true));

        assertThat(saved.getId()).isEqualTo(P_1);
        assertThat(saved.getName()).isEqualTo("Widget Alpha");
        assertThat(saved.getPrice()).isEqualByComparingTo("49.99");
        assertThat(saved.isAvailable()).isTrue();
    }

    @Test
    @DisplayName("save() updates an existing product (price + availability persist)")
    void save_updatesExistingProduct() {
        productRepository.save(Product.create(P_1, "Widget Alpha", new BigDecimal("49.99"), true));

        Product changed = Product.create(P_1, "Widget Alpha", new BigDecimal("59.99"), false);
        productRepository.save(changed);

        Product fromDb = productRepository.findById(P_1).orElseThrow();
        assertThat(fromDb.getPrice()).isEqualByComparingTo("59.99");
        assertThat(fromDb.isAvailable()).isFalse();
    }

    // ── FindById ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("findById() returns the product when it exists")
    void findById_found() {
        productRepository.save(Product.create(P_1, "Widget Alpha", new BigDecimal("49.99"), true));

        Optional<Product> result = productRepository.findById(P_1);

        assertThat(result).isPresent();
        assertThat(result.get().getName()).isEqualTo("Widget Alpha");
    }

    @Test
    @DisplayName("findById() returns empty Optional for a non-existent id")
    void findById_notFound() {
        assertThat(productRepository.findById(UUID.randomUUID())).isEmpty();
    }

    // ── FindAll ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("findAll() returns every persisted product")
    void findAll_returnsAll() {
        productRepository.save(Product.create(P_1, "Widget Alpha", new BigDecimal("49.99"), true));
        productRepository.save(Product.create(P_2, "Widget Beta", new BigDecimal("19.99"), false));

        List<Product> all = productRepository.findAll();

        assertThat(all).extracting(Product::getId)
                .containsExactlyInAnyOrder(P_1, P_2);
    }

    // ── DeleteById ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("deleteById() removes the product")
    void deleteById_removesProduct() {
        productRepository.save(Product.create(P_1, "Widget Alpha", new BigDecimal("49.99"), true));

        productRepository.deleteById(P_1);

        assertThat(productRepository.findById(P_1)).isEmpty();
    }
}
