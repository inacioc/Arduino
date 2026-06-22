package com.example.ordermanagement.domain.model;

import java.math.BigDecimal;

/**
 * Product domain entity.
 * <p>
 * A richer replacement for the anemic {@code ProductServicePort.ProductInfo} record:
 * it guards its own invariants and exposes business behaviour rather than being a
 * bare data carrier. The product catalogue still lives in an external service, so
 * adapters are expected to map their transport representation into this model.
 */
public class Product {

    private final String id;
    private String name;
    private BigDecimal price;
    private boolean available;

    private Product(String id, String name, BigDecimal price, boolean available) {
        this.id        = id;
        this.name      = name;
        this.price     = price;
        this.available = available;
    }

    // ── Factory method ──────────────────────────────────────────────────────────

    public static Product create(String id, String name, BigDecimal price, boolean available) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Product id must not be blank");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Product name must not be blank");
        }
        if (price == null || price.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Product price must be positive");
        }
        return new Product(id, name, price, available);
    }

    // ── Behaviour ───────────────────────────────────────────────────────────────

    /** Whether the product can currently be ordered. */
    public boolean isOrderable() {
        return available;
    }

    public void markAvailable() {
        this.available = true;
    }

    public void markUnavailable() {
        this.available = false;
    }

    public void rename(String newName) {
        if (newName == null || newName.isBlank()) {
            throw new IllegalArgumentException("Product name must not be blank");
        }
        this.name = newName;
    }

    public void changePrice(BigDecimal newPrice) {
        if (newPrice == null || newPrice.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Product price must be positive");
        }
        this.price = newPrice;
    }

    /** Price for a given quantity of this product. */
    public BigDecimal priceFor(int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be positive");
        }
        return price.multiply(BigDecimal.valueOf(quantity));
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    public String getId()        { return id; }
    public String getName()      { return name; }
    public BigDecimal getPrice() { return price; }
    public boolean isAvailable() { return available; }
}
