package com.example.ordermanagement.frontend.web;

import com.example.ordermanagement.frontend.client.OrderApiClient;
import com.example.ordermanagement.frontend.client.ProductApiClient;
import com.example.ordermanagement.frontend.client.dto.CreateOrderRequestDto;
import com.example.ordermanagement.frontend.client.dto.OrderDto;
import com.example.ordermanagement.frontend.client.dto.OrderStatus;
import com.example.ordermanagement.frontend.client.exception.BackendApiException;
import com.example.ordermanagement.frontend.client.exception.BackendConflictException;
import com.example.ordermanagement.frontend.client.exception.BackendNotFoundException;
import com.example.ordermanagement.frontend.client.exception.BackendOrderValidationException;
import com.example.ordermanagement.frontend.client.exception.OrderItemErrorDto;
import com.example.ordermanagement.frontend.web.form.CreateOrderForm;
import com.example.ordermanagement.frontend.web.form.OrderItemForm;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.UUID;
import java.util.function.Function;

@Controller
@RequestMapping("/orders")
public class OrderMvcController {

    private final OrderApiClient orderApi;
    private final ProductApiClient productApi;

    public OrderMvcController(OrderApiClient orderApi, ProductApiClient productApi) {
        this.orderApi = orderApi;
        this.productApi = productApi;
    }

    @GetMapping
    public String list(@RequestParam(required = false) OrderStatus status, Model model) {
        List<OrderDto> orders = status != null ? orderApi.findByStatus(status) : orderApi.findAll();
        model.addAttribute("orders", orders);
        model.addAttribute("statuses", OrderStatus.values());
        model.addAttribute("selectedStatus", status);
        return "orders/list";
    }

    @GetMapping("/new")
    public String newForm(Model model) {
        model.addAttribute("orderForm", new CreateOrderForm());
        addProductsToModel(model);
        return "orders/form";
    }

    @PostMapping
    public String create(@Valid @ModelAttribute("orderForm") CreateOrderForm form,
                          BindingResult bindingResult,
                          Model model) {
        if (bindingResult.hasErrors()) {
            addProductsToModel(model);
            return "orders/form";
        }
        try {
            CreateOrderRequestDto request = toRequest(form);
            OrderDto created = orderApi.create(request);
            return "redirect:/orders/" + created.id();
        } catch (BackendOrderValidationException e) {
            mapItemErrors(form, e.getErrors(), bindingResult);
            addProductsToModel(model);
            return "orders/form";
        } catch (BackendApiException e) {
            bindingResult.reject("order.invalid", e.getMessage());
            addProductsToModel(model);
            return "orders/form";
        }
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable UUID id, Model model) {
        OrderDto order = orderApi.findById(id)
                .orElseThrow(() -> new BackendNotFoundException("Order " + id + " not found"));
        model.addAttribute("order", order);
        return "orders/detail";
    }

    @PutMapping("/{id}/confirm")
    public String confirmOrder(@PathVariable UUID id, RedirectAttributes redirectAttributes) {
        return transition(id, orderApi::confirm, redirectAttributes);
    }

    @PutMapping("/{id}/complete")
    public String completeOrder(@PathVariable UUID id, RedirectAttributes redirectAttributes) {
        return transition(id, orderApi::complete, redirectAttributes);
    }

    @PutMapping("/{id}/cancel")
    public String cancelOrder(@PathVariable UUID id, RedirectAttributes redirectAttributes) {
        return transition(id, orderApi::cancel, redirectAttributes);
    }

    private String transition(UUID id, Function<UUID, OrderDto> action, RedirectAttributes redirectAttributes) {
        try {
            action.apply(id);
            redirectAttributes.addFlashAttribute("successMessage", "Order updated");
        } catch (BackendConflictException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/orders/" + id;
    }

    private void addProductsToModel(Model model) {
        model.addAttribute("products", productApi.findAll());
    }

    private CreateOrderRequestDto toRequest(CreateOrderForm form) {
        List<CreateOrderRequestDto.OrderItemRequestDto> items = form.getItems().stream()
                .map(item -> new CreateOrderRequestDto.OrderItemRequestDto(
                        item.getProductId(), item.getQuantity(), item.getUnitPrice()))
                .toList();
        return new CreateOrderRequestDto(form.getCustomerId(), items);
    }

    private void mapItemErrors(CreateOrderForm form, List<OrderItemErrorDto> errors, BindingResult bindingResult) {
        List<OrderItemForm> items = form.getItems();
        for (OrderItemErrorDto error : errors) {
            boolean mapped = false;
            for (int i = 0; i < items.size(); i++) {
                if (error.productId().equals(items.get(i).getProductId())) {
                    bindingResult.rejectValue("items[" + i + "].productId", error.code(), error.message());
                    mapped = true;
                    break;
                }
            }
            if (!mapped) {
                bindingResult.reject(error.code(), error.message());
            }
        }
    }
}
