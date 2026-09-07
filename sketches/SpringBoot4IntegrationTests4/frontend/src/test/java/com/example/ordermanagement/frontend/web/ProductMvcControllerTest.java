package com.example.ordermanagement.frontend.web;

import com.example.ordermanagement.frontend.client.ProductApiClient;
import com.example.ordermanagement.frontend.client.dto.CreateProductRequestDto;
import com.example.ordermanagement.frontend.client.dto.ProductDto;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(ProductMvcController.class)
class ProductMvcControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProductApiClient productApi;

    @Test
    void listRendersProducts() throws Exception {
        var product = new ProductDto(UUID.randomUUID(), "Widget", new BigDecimal("9.99"), true);
        org.mockito.BDDMockito.given(productApi.findAll()).willReturn(List.of(product));

        mockMvc.perform(get("/products"))
                .andExpect(status().isOk())
                .andExpect(view().name("products/list"))
                .andExpect(model().attribute("products", List.of(product)));
    }

    @Test
    void blankNameIsRejectedWithFieldError() throws Exception {
        mockMvc.perform(post("/products")
                        .param("name", "")
                        .param("price", "10.00")
                        .param("available", "true"))
                .andExpect(status().isOk())
                .andExpect(view().name("products/form"))
                .andExpect(model().attributeHasFieldErrors("productForm", "name"));

        verify(productApi, never()).create(any());
    }

    @Test
    void nonPositivePriceIsRejectedWithFieldError() throws Exception {
        mockMvc.perform(post("/products")
                        .param("name", "Widget")
                        .param("price", "0")
                        .param("available", "true"))
                .andExpect(status().isOk())
                .andExpect(view().name("products/form"))
                .andExpect(model().attributeHasFieldErrors("productForm", "price"));
    }

    @Test
    void validSubmissionRedirectsToList() throws Exception {
        org.mockito.BDDMockito.given(productApi.create(any(CreateProductRequestDto.class)))
                .willReturn(new ProductDto(UUID.randomUUID(), "Widget", new BigDecimal("10.00"), true));

        mockMvc.perform(post("/products")
                        .param("name", "Widget")
                        .param("price", "10.00")
                        .param("available", "true"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/products"));
    }
}
