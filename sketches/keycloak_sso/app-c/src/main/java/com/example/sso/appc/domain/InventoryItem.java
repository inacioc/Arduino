package com.example.sso.appc.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

/** Application C's business data, in a temporary in-memory database. */
@Entity
public class InventoryItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String sku;

    private String description;

    private int quantity;

    protected InventoryItem() {
        // for JPA
    }

    public InventoryItem(String sku, String description, int quantity) {
        this.sku = sku;
        this.description = description;
        this.quantity = quantity;
    }

    public Long getId() {
        return this.id;
    }

    public String getSku() {
        return this.sku;
    }

    public String getDescription() {
        return this.description;
    }

    public int getQuantity() {
        return this.quantity;
    }
}
