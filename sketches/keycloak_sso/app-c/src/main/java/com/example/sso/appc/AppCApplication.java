package com.example.sso.appc;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Application C (port 8083) - a pure API living in a <em>different</em> SSO domain.
 *
 * <p>It trusts Keycloak on port 8090 and nothing else. Users of domain A do not exist here; a token
 * signed by domain A is rejected outright because it is signed with keys Application C never
 * fetches and carries an issuer it does not accept.
 *
 * <p>For Application A to call it, an administrator of <em>this</em> domain had to issue Application A
 * its own client credentials ({@code app-a-federated-m2m}). That is the entire cross-domain
 * machine-to-machine story: no trust relationship between the identity providers, just a second set
 * of credentials held by the caller.
 */
@SpringBootApplication
public class AppCApplication {

    public static void main(String[] args) {
        SpringApplication.run(AppCApplication.class, args);
    }
}
