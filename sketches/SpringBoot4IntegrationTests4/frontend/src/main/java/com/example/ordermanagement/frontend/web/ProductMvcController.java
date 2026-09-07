package com.example.ordermanagement.frontend.web;

import com.example.ordermanagement.frontend.client.ProductApiClient;
import com.example.ordermanagement.frontend.client.dto.CreateProductRequestDto;
import com.example.ordermanagement.frontend.client.exception.BackendApiException;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.UUID;

import com.example.ordermanagement.frontend.web.form.CreateProductForm;

@Controller
@RequestMapping("/products")
public class ProductMvcController {

    private final ProductApiClient productApi;

    public ProductMvcController(ProductApiClient productApi) {
        this.productApi = productApi;
    }

    @GetMapping
    public String list(Model model) {
        model.addAttribute("products", productApi.findAll());
        return "products/list";
    }

    @GetMapping("/new")
    public String newForm(Model model) {
        model.addAttribute("productForm", new CreateProductForm());
        return "products/form";
    }

    @PostMapping
    public String create(@Valid @ModelAttribute("productForm") CreateProductForm form,
                          BindingResult bindingResult,
                          RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            return "products/form";
        }
        try {
            CreateProductRequestDto request = new CreateProductRequestDto(
                    UUID.randomUUID(), form.getName(), form.getPrice(), form.isAvailable());
            productApi.create(request);
        } catch (BackendApiException e) {
            bindingResult.reject("product.invalid", e.getMessage());
            return "products/form";
        }
        redirectAttributes.addFlashAttribute("successMessage", "Product created");
        return "redirect:/products";
    }
}
