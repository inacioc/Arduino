package com.example.ordermanagement.messaging;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Executable JMS listener application.
 * <p>
 * Lives in its own package (not the shared root) so it does not collide with the
 * web app's {@code @SpringBootApplication} when the web module depends on this one.
 * Component scanning is limited to the messaging adapter and its JMS config — the
 * domain {@code @Service} beans are not scanned (they would need outbound ports
 * not present on this module's classpath).
 */
@SpringBootApplication(scanBasePackages = {
        "com.example.ordermanagement.infrastructure.adapter.out.messaging",
        "com.example.ordermanagement.infrastructure.config"
})
public class MessagingApplication {

    public static void main(String[] args) {
        SpringApplication.run(MessagingApplication.class, args);
    }
}
