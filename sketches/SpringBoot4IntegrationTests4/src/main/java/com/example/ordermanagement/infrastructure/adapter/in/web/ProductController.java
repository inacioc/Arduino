package com.example.ordermanagement.infrastructure.adapter.in.web;

import com.example.ordermanagement.domain.port.in.GetProductUseCase;
import com.example.ordermanagement.domain.port.in.SaveProductUseCase;
import com.example.ordermanagement.domain.port.in.SaveProductUseCase.SaveProductCommand;
import com.example.ordermanagement.infrastructure.adapter.in.web.dto.CreateProductRequest;
import com.example.ordermanagement.infrastructure.adapter.in.web.dto.ProductResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final SaveProductUseCase saveProduct;
    private final GetProductUseCase getProduct;

    public ProductController(SaveProductUseCase saveProduct, GetProductUseCase getProduct) {
        this.saveProduct = saveProduct;
        this.getProduct  = getProduct;
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ProductResponse> create(@Valid @RequestBody CreateProductRequest request) {
        SaveProductCommand command = new SaveProductCommand(
                request.id(), request.name(), request.price(), request.available());
        ProductResponse body = ProductResponse.from(saveProduct.save(command));
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'ADMIN')")
    public ResponseEntity<ProductResponse> getById(@PathVariable String id) {
        return getProduct.findProduct(id)
                .map(ProductResponse::from)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('CUSTOMER', 'ADMIN')")
    public List<ProductResponse> getAll() {
        return getProduct.findAll().stream()
                .map(ProductResponse::from)
                .toList();
    }
}
