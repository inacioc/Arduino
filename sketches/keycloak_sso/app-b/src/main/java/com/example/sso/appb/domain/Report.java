package com.example.sso.appb.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

/**
 * The business data Application B protects. Nothing clever - it exists so that a successful
 * authorization has something real to return, and lives in an in-memory database that is recreated
 * on every start.
 */
@Entity
public class Report {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String title;

    private String owner;

    private String status;

    protected Report() {
        // for JPA
    }

    public Report(String title, String owner, String status) {
        this.title = title;
        this.owner = owner;
        this.status = status;
    }

    public Long getId() {
        return this.id;
    }

    public String getTitle() {
        return this.title;
    }

    public String getOwner() {
        return this.owner;
    }

    public String getStatus() {
        return this.status;
    }
}
