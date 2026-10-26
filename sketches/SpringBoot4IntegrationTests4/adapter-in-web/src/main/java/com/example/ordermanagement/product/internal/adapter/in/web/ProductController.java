package com.example.ordermanagement.product.internal.adapter.in.web;

import com.example.ordermanagement.product.ProductNotFoundException;
import com.example.ordermanagement.product.DeleteProductUseCase;
import com.example.ordermanagement.product.GetProductUseCase;
import com.example.ordermanagement.product.SaveProductUseCase;
import com.example.ordermanagement.product.SaveProductUseCase.SaveProductCommand;
import com.example.ordermanagement.product.internal.adapter.in.web.CreateProductRequest;
import com.example.ordermanagement.product.internal.adapter.in.web.ProductResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final SaveProductUseCase saveProduct;
    private final GetProductUseCase getProduct;
    private final DeleteProductUseCase deleteProduct;

    public ProductController(SaveProductUseCase saveProduct, GetProductUseCase getProduct,
                              DeleteProductUseCase deleteProduct) {
        this.saveProduct   = saveProduct;
        this.getProduct    = getProduct;
        this.deleteProduct = deleteProduct;
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
    public ProductResponse getById(@PathVariable UUID id) {
        return getProduct.findProduct(id)
                .map(ProductResponse::from)
                .orElseThrow(() -> new ProductNotFoundException(id.toString()));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('CUSTOMER', 'ADMIN')")
    public List<ProductResponse> getAll() {
        return getProduct.findAll().stream()
                .map(ProductResponse::from)
                .toList();
    }

    @GetMapping("/search")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'ADMIN')")
    public ProductResponse getByName(@RequestParam String name) {
        return getProduct.findByName(name)
                .map(ProductResponse::from)
                .orElseThrow(() -> new ProductNotFoundException(name));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        deleteProduct.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
