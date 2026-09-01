package com.example.sso.appa;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Application A (port 8081).
 *
 * <p>Plays three different roles in this lab, on purpose:
 * <ol>
 *   <li><b>OIDC client</b> in SSO domain A - humans log in here with a browser.</li>
 *   <li><b>Machine-to-machine caller</b> - two scheduled jobs call Application B (same SSO
 *       domain) and Application C (a different SSO domain) using the {@code client_credentials}
 *       grant, i.e. with Application A's <em>own</em> identity, no user involved.</li>
 *   <li><b>Delegating client</b> - a user-triggered page calls the very same Application B
 *       endpoint while forwarding the <em>logged-in user's</em> access token.</li>
 * </ol>
 */
@SpringBootApplication
@EnableScheduling
public class AppAApplication {

    public static void main(String[] args) {
        SpringApplication.run(AppAApplication.class, args);
    }
}
