package com.example.ordermanagement.adapter.in.web;

import com.example.ordermanagement.domain.port.in.GetProductUseCase;
import com.example.ordermanagement.domain.port.in.ProductResult;
import com.example.ordermanagement.domain.port.in.SaveProductUseCase;
import com.example.ordermanagement.domain.port.in.SaveProductUseCase.SaveProductCommand;
import com.example.ordermanagement.infrastructure.adapter.in.web.ProductController;
import com.example.ordermanagement.infrastructure.adapter.in.web.dto.CreateProductRequest;
import com.example.ordermanagement.infrastructure.config.SecurityConfig;
import com.example.ordermanagement.support.JwtHelper;
import com.example.ordermanagement.support.WebSecurityTestConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web-slice tests for ProductController — MVC layer only, use-case ports mocked.
 */
@WebMvcTest(ProductController.class)
@Import({SecurityConfig.class, WebSecurityTestConfig.class})
class ProductControllerIT {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockBean private SaveProductUseCase saveProduct;
    @MockBean private GetProductUseCase getProduct;

    private static final UUID PRODUCT_ID = UUID.fromString("77777777-0000-0000-0000-000000000001");

    private static ProductResult productResult() {
        return new ProductResult(PRODUCT_ID, "Widget Alpha", new BigDecimal("49.99"), true);
    }

    private static CreateProductRequest request(String name) {
        return new CreateProductRequest(PRODUCT_ID, name, new BigDecimal("49.99"), true);
    }

    @Test
    @DisplayName("POST /api/products - ADMIN creates a product and returns 201")
    void create_asAdmin_returns201() throws Exception {
        when(saveProduct.save(any(SaveProductCommand.class))).thenReturn(productResult());

        mockMvc.perform(post("/api/products")
                        .with(JwtHelper.adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request("Widget Alpha"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(PRODUCT_ID.toString()))
                .andExpect(jsonPath("$.name").value("Widget Alpha"))
                .andExpect(jsonPath("$.available").value(true));
    }

    @Test
    @DisplayName("POST /api/products - returns 403 when a CUSTOMER tries to create")
    void create_asCustomer_forbidden() throws Exception {
        mockMvc.perform(post("/api/products")
                        .with(JwtHelper.customerToken("customer-1"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request("Widget Alpha"))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /api/products - returns 401 when unauthenticated")
    void create_noAuth_unauthorized() throws Exception {
        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request("Widget Alpha"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /api/products - returns 400 when the body is invalid")
    void create_invalid_returns400() throws Exception {
        mockMvc.perform(post("/api/products")
                        .with(JwtHelper.adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request("  "))))  // blank name
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors").isArray());
    }

    @Test
    @DisplayName("GET /api/products/{id} - returns 200 when found")
    void getById_found() throws Exception {
        when(getProduct.findProduct(any(UUID.class))).thenReturn(Optional.of(productResult()));

        mockMvc.perform(get("/api/products/{id}", PRODUCT_ID)
                        .with(JwtHelper.customerToken("customer-1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(PRODUCT_ID.toString()))
                .andExpect(jsonPath("$.name").value("Widget Alpha"));
    }

    @Test
    @DisplayName("GET /api/products/{id} - returns 404 when not found")
    void getById_notFound() throws Exception {
        when(getProduct.findProduct(any(UUID.class))).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/products/{id}", UUID.randomUUID())
                        .with(JwtHelper.customerToken("customer-1")))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /api/products - lists all products")
    void getAll_returnsProducts() throws Exception {
        when(getProduct.findAll()).thenReturn(List.of(
                productResult(),
                new ProductResult(UUID.randomUUID(), "Widget Beta", new BigDecimal("19.99"), false)));

        mockMvc.perform(get("/api/products")
                        .with(JwtHelper.adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));
    }
}
