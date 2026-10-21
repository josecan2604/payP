package com.arana.authserver;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Auth server. Exchanges client credentials for a short-lived RS256 JWT (with role claims)
 * and publishes its public key at a JWKS endpoint so the gateway can verify tokens.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class AuthServerApplication {
    public static void main(String[] args) {
        SpringApplication.run(AuthServerApplication.class, args);
    }
}
