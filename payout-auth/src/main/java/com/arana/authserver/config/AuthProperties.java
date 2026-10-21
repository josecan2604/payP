package com.arana.authserver.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;
import java.util.Map;

/** Binds "auth.*": the issuer name, token lifetime, and the registered clients + their roles. */
@ConfigurationProperties(prefix = "auth")
public record AuthProperties(
        String issuer,
        long tokenTtlSeconds,
        Map<String, Client> clients
) {
    public record Client(String secret, List<String> roles) {}
}
