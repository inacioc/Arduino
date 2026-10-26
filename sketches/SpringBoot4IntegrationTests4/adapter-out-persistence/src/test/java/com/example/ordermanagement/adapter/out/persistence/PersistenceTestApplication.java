package com.example.ordermanagement.adapter.out.persistence;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Minimal Spring Boot context for persistence-adapter integration tests.
 * <p>
 * This module has no {@code @SpringBootApplication} of its own (it is a library),
 * so the slice tests anchor on this configuration. It boots only JPA + Flyway + the
 * persistence adapter beans — no web, no MQ, no batch.
 */
@SpringBootConfiguration
@EnableAutoConfiguration
@ComponentScan({"com.example.ordermanagement.order.internal.adapter.out.persistence", "com.example.ordermanagement.customer.internal.adapter.out.persistence", "com.example.ordermanagement.product.internal.adapter.out.persistence", "com.example.ordermanagement.shared"})
@EntityScan({"com.example.ordermanagement.order.internal.adapter.out.persistence", "com.example.ordermanagement.customer.internal.adapter.out.persistence", "com.example.ordermanagement.product.internal.adapter.out.persistence"})
@EnableJpaRepositories({"com.example.ordermanagement.order.internal.adapter.out.persistence", "com.example.ordermanagement.customer.internal.adapter.out.persistence", "com.example.ordermanagement.product.internal.adapter.out.persistence"})
public class PersistenceTestApplication {
}
