package com.example.ordermanagement.domain.model;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Product domain entity.
 * <p>
 * A rich aggregate that guards its own invariants and exposes business
 * behaviour rather than being a bare data carrier. The product catalogue is
 * persisted locally via {@code ProductRepositoryPort}; the persistence adapter
 * maps this model to and from its JPA entity.
 */
public class Product {

    private final UUID id;
    private String name;
    private BigDecimal price;
    private boolean available;

    private Product(UUID id, String name, BigDecimal price, boolean available) {
        this.id        = id;
        this.name      = name;
        this.price     = price;
        this.available = available;
    }

    // ── Factory method ──────────────────────────────────────────────────────────

    public static Product create(UUID id, String name, BigDecimal price, boolean available) {
        if (id == null) {
            throw new IllegalArgumentException("Product id must not be null");
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

    public UUID getId()          { return id; }
    public String getName()      { return name; }
    public BigDecimal getPrice() { return price; }
    public boolean isAvailable() { return available; }
}
