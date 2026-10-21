package com.arana.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * The single public entry point. It:
 *   1. validates the JWT on each request (signature via the auth server's JWKS),
 *   2. enforces RBAC from the token's role claims (e.g. only ADMIN may reconcile),
 *   3. forwards the authenticated identity to the resource server as trusted headers,
 *   4. routes the request to the right backend.
 */
@SpringBootApplication
public class GatewayApplication {
    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }
}
