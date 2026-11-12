package com.example.ordermanagement;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point of the application: the only executable class of the project.
 * <p>
 * Lives in the {@code bootstrap} Maven module, whose dependencies decide which adapters are
 * started (today: REST API, persistence, IBM MQ, Modulith events). It scans the business
 * modules (order, customer, product, notification), {@code shared} and {@code infrastructure};
 * it deliberately does NOT scan the bare root package, so the sibling {@code MessagingApplication}
 * and {@code BatchApplication} (standalone apps of other modules) are neither detected nor
 * component-scanned.
 */
@SpringBootApplication(scanBasePackages = {
        "com.example.ordermanagement.order",
        "com.example.ordermanagement.customer",
        "com.example.ordermanagement.product",
        "com.example.ordermanagement.notification",
        "com.example.ordermanagement.shared",
        "com.example.ordermanagement.infrastructure"
})
public class OrderManagementApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrderManagementApplication.class, args);
    }
}
