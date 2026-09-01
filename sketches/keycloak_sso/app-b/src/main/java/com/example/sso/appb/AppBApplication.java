package com.example.sso.appb;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Application B (port 8082).
 *
 * <p>Two faces, one process:
 * <ul>
 *   <li><b>Browser application</b> - a human who already signed in to Application A reaches
 *       {@code /ui/reports} without typing a password again. That is SSO, and it works because
 *       Application A and Application B are separate clients of the <em>same</em> realm.</li>
 *   <li><b>Resource server</b> - {@code /api/**} accepts only a valid bearer token issued by
 *       SSO domain A, and is what Application A's scheduler calls.</li>
 * </ul>
 */
@SpringBootApplication
public class AppBApplication {

    public static void main(String[] args) {
        SpringApplication.run(AppBApplication.class, args);
    }
}
