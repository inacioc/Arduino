package com.example.ordermanagement.adapter.in.web;

import com.example.ordermanagement.domain.model.Customer;
import com.example.ordermanagement.domain.port.out.CustomerRepositoryPort;
import com.example.ordermanagement.infrastructure.adapter.in.web.dto.CreateCustomerRequest;
import com.example.ordermanagement.support.IntegrationTestBase;
import com.example.ordermanagement.support.JwtHelper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.jdbc.Sql;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for CustomerController.
 *
 * Mirrors {@link ProductControllerIT}: full Spring context, real PostgreSQL, Keycloak
 * mocked via {@code jwt()}. The customer table IS the persistence under test here, so
 * these tests use the real CustomerPersistenceAdapter and clean the customers table
 * before each test.
 */
@Sql(scripts = "/sql/clean-customers.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class CustomerControllerIT extends IntegrationTestBase {

    @Autowired
    private CustomerRepositoryPort customerRepository;

    // ── POST /api/customers ────────────────────────────────────────────────────

    @Test
    @DisplayName("POST /api/customers - ADMIN creates a customer and returns 201")
    void create_asAdmin_returns201() throws Exception {
        CreateCustomerRequest request = new CreateCustomerRequest(
                "Ada", "Lovelace", "555-0100", "ada@example.com");

        mockMvc.perform(post("/api/customers")
                        .with(JwtHelper.adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("ada@example.com"))
                .andExpect(jsonPath("$.firstName").value("Ada"));
    }

    @Test
    @DisplayName("POST /api/customers - returns 403 when a CUSTOMER tries to create")
    void create_asCustomer_forbidden() throws Exception {
        CreateCustomerRequest request = new CreateCustomerRequest(
                "Ada", "Lovelace", "555-0100", "ada@example.com");

        mockMvc.perform(post("/api/customers")
                        .with(JwtHelper.customerToken("customer-1"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /api/customers - returns 409 when a customer with the same email already exists")
    void create_duplicateEmail_returns409() throws Exception {
        customerRepository.save(Customer.create("Ada", "Lovelace", "555-0100", "ada@example.com"));

        CreateCustomerRequest request = new CreateCustomerRequest(
                "Charles", "Babbage", "555-0101", "ada@example.com");

        mockMvc.perform(post("/api/customers")
                        .with(JwtHelper.adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors[0].code").value("CUSTOMER_ALREADY_EXISTS"));
    }

    // ── GET /api/customers/{id} ────────────────────────────────────────────────

    @Test
    @DisplayName("GET /api/customers/{id} - returns 200 with the customer when found")
    void getById_found() throws Exception {
        Customer saved = customerRepository.save(
                Customer.create("Ada", "Lovelace", "555-0100", "ada@example.com"));

        mockMvc.perform(get("/api/customers/{id}", saved.getId())
                        .with(JwtHelper.customerToken("customer-1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(saved.getId().toString()))
                .andExpect(jsonPath("$.email").value("ada@example.com"));
    }

    @Test
    @DisplayName("GET /api/customers/{id} - returns 404 when the customer does not exist")
    void getById_notFound() throws Exception {
        mockMvc.perform(get("/api/customers/{id}", UUID.randomUUID())
                        .with(JwtHelper.customerToken("customer-1")))
                .andExpect(status().isNotFound());
    }

    // ── GET /api/customers ─────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /api/customers - lists all customers")
    void getAll_returnsCustomers() throws Exception {
        customerRepository.save(Customer.create("Ada", "Lovelace", "555-0100", "ada@example.com"));
        customerRepository.save(Customer.create("Charles", "Babbage", "555-0101", "charles@example.com"));

        mockMvc.perform(get("/api/customers")
                        .with(JwtHelper.adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$", hasSize(2)));
    }

    // ── GET /api/customers/search?email= ───────────────────────────────────────

    @Test
    @DisplayName("GET /api/customers/search?email= - returns 200 with the customer when found")
    void getByEmail_found() throws Exception {
        Customer saved = customerRepository.save(
                Customer.create("Ada", "Lovelace", "555-0100", "ada@example.com"));

        mockMvc.perform(get("/api/customers/search")
                        .param("email", "ada@example.com")
                        .with(JwtHelper.adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(saved.getId().toString()));
    }

    @Test
    @DisplayName("GET /api/customers/search?email= - returns 404 when no customer holds that email")
    void getByEmail_notFound() throws Exception {
        mockMvc.perform(get("/api/customers/search")
                        .param("email", "nobody@example.com")
                        .with(JwtHelper.adminToken()))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /api/customers/search?email= - returns 403 for CUSTOMER role")
    void getByEmail_customerForbidden() throws Exception {
        mockMvc.perform(get("/api/customers/search")
                        .param("email", "ada@example.com")
                        .with(JwtHelper.customerToken("customer-1")))
                .andExpect(status().isForbidden());
    }

    // ── DELETE /api/customers/{id} ─────────────────────────────────────────────

    @Test
    @DisplayName("DELETE /api/customers/{id} - ADMIN deletes an existing customer and returns 204")
    void delete_asAdmin_returns204() throws Exception {
        Customer saved = customerRepository.save(
                Customer.create("Ada", "Lovelace", "555-0100", "ada@example.com"));

        mockMvc.perform(delete("/api/customers/{id}", saved.getId())
                        .with(JwtHelper.adminToken()))
                .andExpect(status().isNoContent());

        assertThat(customerRepository.findById(saved.getId())).isEmpty();
    }

    @Test
    @DisplayName("DELETE /api/customers/{id} - returns 404 when the customer does not exist")
    void delete_notFound_returns404() throws Exception {
        mockMvc.perform(delete("/api/customers/{id}", UUID.randomUUID())
                        .with(JwtHelper.adminToken()))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("DELETE /api/customers/{id} - returns 403 when a CUSTOMER tries to delete")
    void delete_asCustomer_forbidden() throws Exception {
        Customer saved = customerRepository.save(
                Customer.create("Ada", "Lovelace", "555-0100", "ada@example.com"));

        mockMvc.perform(delete("/api/customers/{id}", saved.getId())
                        .with(JwtHelper.customerToken("customer-1")))
                .andExpect(status().isForbidden());
    }
}
