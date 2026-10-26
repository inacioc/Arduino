package com.example.ordermanagement;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Executable REST server.
 * <p>
 * Scans {@code domain} (services) and {@code infrastructure} (controllers, config,
 * and the persistence + messaging adapters it depends on), wiring the full
 * inbound→domain→outbound flow. It deliberately does NOT scan the bare root package,
 * so the sibling {@code MessagingApplication} (pulled in via the messaging dependency)
 * is neither detected nor component-scanned.
 */
@SpringBootApplication(scanBasePackages = {
        "com.example.ordermanagement.order",
        "com.example.ordermanagement.customer",
        "com.example.ordermanagement.product",
        "com.example.ordermanagement.notification",
        "com.example.ordermanagement.shared",
        "com.example.ordermanagement.infrastructure"
})
public class WebApplication {

    public static void main(String[] args) {
        SpringApplication.run(WebApplication.class, args);
    }
}
