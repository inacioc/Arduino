package com.example.ordermanagement.adapter.in.web;

import com.example.ordermanagement.domain.model.Product;
import com.example.ordermanagement.domain.port.out.ProductRepositoryPort;
import com.example.ordermanagement.infrastructure.adapter.in.web.dto.CreateProductRequest;
import com.example.ordermanagement.support.IntegrationTestBase;
import com.example.ordermanagement.support.JwtHelper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.jdbc.Sql;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for ProductController.
 *
 * Mirrors {@link OrderControllerIT}: full Spring context, real PostgreSQL, Keycloak
 * mocked via {@code jwt()}. Unlike orders, the product catalogue IS the persistence
 * under test here, so these tests use the real ProductPersistenceAdapter and clean
 * the products table before each test.
 */
@Sql(scripts = "/sql/clean-products.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class ProductControllerIT extends IntegrationTestBase {

    @Autowired
    private ProductRepositoryPort productRepository;

    private static final UUID PRODUCT_ID = UUID.fromString("77777777-0000-0000-0000-000000000001");

    // ── POST /api/products ─────────────────────────────────────────────────────

    @Test
    @DisplayName("POST /api/products - ADMIN creates a product and returns 201")
    void create_asAdmin_returns201() throws Exception {
        CreateProductRequest request = new CreateProductRequest(
                PRODUCT_ID, "Widget Alpha", new BigDecimal("49.99"), true);

        mockMvc.perform(post("/api/products")
                        .with(JwtHelper.adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(PRODUCT_ID.toString()))
                .andExpect(jsonPath("$.name").value("Widget Alpha"))
                .andExpect(jsonPath("$.price").value(49.99))
                .andExpect(jsonPath("$.available").value(true));

        // persisted for real
        assertThat(productRepository.findById(PRODUCT_ID)).isPresent();
    }

    @Test
    @DisplayName("POST /api/products - returns 403 when a CUSTOMER tries to create")
    void create_asCustomer_forbidden() throws Exception {
        CreateProductRequest request = new CreateProductRequest(
                PRODUCT_ID, "Widget Alpha", new BigDecimal("49.99"), true);

        mockMvc.perform(post("/api/products")
                        .with(JwtHelper.customerToken("customer-1"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /api/products - returns 401 when unauthenticated")
    void create_noAuth_unauthorized() throws Exception {
        CreateProductRequest request = new CreateProductRequest(
                PRODUCT_ID, "Widget Alpha", new BigDecimal("49.99"), true);

        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /api/products - returns 400 when the body is invalid")
    void create_invalid_returns400() throws Exception {
        CreateProductRequest request = new CreateProductRequest(
                PRODUCT_ID, "  ", new BigDecimal("49.99"), true);  // blank name

        mockMvc.perform(post("/api/products")
                        .with(JwtHelper.adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors").isArray());
    }

    // ── GET /api/products/{id} ─────────────────────────────────────────────────

    @Test
    @DisplayName("GET /api/products/{id} - returns 200 with the product when found")
    void getById_found() throws Exception {
        productRepository.save(Product.create(PRODUCT_ID, "Widget Alpha", new BigDecimal("49.99"), true));

        mockMvc.perform(get("/api/products/{id}", PRODUCT_ID)
                        .with(JwtHelper.customerToken("customer-1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(PRODUCT_ID.toString()))
                .andExpect(jsonPath("$.name").value("Widget Alpha"));
    }

    @Test
    @DisplayName("GET /api/products/{id} - returns 404 when the product does not exist")
    void getById_notFound() throws Exception {
        mockMvc.perform(get("/api/products/{id}", UUID.randomUUID())
                        .with(JwtHelper.customerToken("customer-1")))
                .andExpect(status().isNotFound());
    }

    // ── GET /api/products ──────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /api/products - lists all products")
    void getAll_returnsProducts() throws Exception {
        productRepository.save(Product.create(PRODUCT_ID, "Widget Alpha", new BigDecimal("49.99"), true));
        productRepository.save(Product.create(UUID.randomUUID(), "Widget Beta", new BigDecimal("19.99"), false));

        mockMvc.perform(get("/api/products")
                        .with(JwtHelper.adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$", hasSize(2)));
    }
}
