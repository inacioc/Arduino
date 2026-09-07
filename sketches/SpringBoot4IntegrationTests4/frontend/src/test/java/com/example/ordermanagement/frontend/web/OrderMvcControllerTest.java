package com.example.ordermanagement.frontend.web;

import com.example.ordermanagement.frontend.client.OrderApiClient;
import com.example.ordermanagement.frontend.client.ProductApiClient;
import com.example.ordermanagement.frontend.client.dto.CreateOrderRequestDto;
import com.example.ordermanagement.frontend.client.dto.OrderDto;
import com.example.ordermanagement.frontend.client.dto.OrderStatus;
import com.example.ordermanagement.frontend.client.dto.ProductDto;
import com.example.ordermanagement.frontend.client.exception.BackendConflictException;
import com.example.ordermanagement.frontend.client.exception.BackendOrderValidationException;
import com.example.ordermanagement.frontend.client.exception.OrderItemErrorDto;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(OrderMvcController.class)
class OrderMvcControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OrderApiClient orderApi;

    @MockitoBean
    private ProductApiClient productApi;

    private static final UUID PRODUCT_ID = UUID.randomUUID();

    @Test
    void noItemsIsRejectedWithFieldError() throws Exception {
        given(productApi.findAll()).willReturn(List.of());

        mockMvc.perform(post("/orders").param("customerId", "cust-1"))
                .andExpect(status().isOk())
                .andExpect(view().name("orders/form"))
                .andExpect(model().attributeHasFieldErrors("orderForm", "items"));
    }

    @Test
    void duplicateProductsTriggerUniqueProductsConstraint() throws Exception {
        given(productApi.findAll()).willReturn(List.of());

        mockMvc.perform(post("/orders")
                        .param("customerId", "cust-1")
                        .param("items[0].productId", PRODUCT_ID.toString())
                        .param("items[0].quantity", "1")
                        .param("items[0].unitPrice", "5.00")
                        .param("items[1].productId", PRODUCT_ID.toString())
                        .param("items[1].quantity", "2")
                        .param("items[1].unitPrice", "5.00"))
                .andExpect(status().isOk())
                .andExpect(view().name("orders/form"))
                .andExpect(model().attributeHasErrors("orderForm"));
    }

    @Test
    void validSubmissionRedirectsToOrderDetail() throws Exception {
        UUID orderId = UUID.randomUUID();
        given(orderApi.create(any(CreateOrderRequestDto.class))).willReturn(
                new OrderDto(orderId, "cust-1", OrderStatus.PENDING, new BigDecimal("10.00"),
                        List.of(), LocalDateTime.now(), LocalDateTime.now()));

        mockMvc.perform(post("/orders")
                        .param("customerId", "cust-1")
                        .param("items[0].productId", PRODUCT_ID.toString())
                        .param("items[0].quantity", "2")
                        .param("items[0].unitPrice", "5.00"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/orders/" + orderId));
    }

    @Test
    void backendValidationErrorMapsToLineFieldError() throws Exception {
        given(productApi.findAll()).willReturn(List.of());
        given(orderApi.create(any(CreateOrderRequestDto.class))).willThrow(
                new BackendOrderValidationException("Order validation failed",
                        List.of(new OrderItemErrorDto(PRODUCT_ID, "PRODUCT_NOT_FOUND", "Product not found"))));

        mockMvc.perform(post("/orders")
                        .param("customerId", "cust-1")
                        .param("items[0].productId", PRODUCT_ID.toString())
                        .param("items[0].quantity", "2")
                        .param("items[0].unitPrice", "5.00"))
                .andExpect(status().isOk())
                .andExpect(view().name("orders/form"))
                .andExpect(model().attributeHasFieldErrors("orderForm", "items[0].productId"));
    }

    @Test
    void missingOrderRendersNotFoundPage() throws Exception {
        given(orderApi.findById(any())).willReturn(Optional.empty());

        mockMvc.perform(get("/orders/{id}", UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(view().name("error/not-found"));
    }

    @Test
    void confirmFailureFlashesErrorMessage() throws Exception {
        UUID orderId = UUID.randomUUID();
        given(orderApi.confirm(orderId)).willThrow(new BackendConflictException("Order is not PENDING"));

        mockMvc.perform(put("/orders/{id}/confirm", orderId))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/orders/" + orderId))
                .andExpect(flash().attributeExists("errorMessage"));
    }
}
