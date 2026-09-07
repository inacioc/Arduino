package com.example.ordermanagement.frontend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * A standalone Spring MVC + Thymeleaf application. It never imports a domain,
 * persistence, or messaging class from the hexagon: everything it knows about
 * orders and products comes from adapter-in-web's REST API, over HTTP, via the
 * DTOs in {@code frontend.client.dto}.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class FrontendApplication {

    public static void main(String[] args) {
        SpringApplication.run(FrontendApplication.class, args);
    }
}
