package com.arana.payout.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Map;

/**
 * Binds the "payout.*" section of application.yml.
 */
@ConfigurationProperties(prefix = "payout")
public record PayoutProperties(
        Map<String, String> clients,
        long tokenTtlSeconds,
        Webhook webhook,
        long statusFlipDelaySeconds,
        Map<String, Double> fx
) {
    public record Webhook(
            String secret,
            String callbackUrl,
            long toleranceSeconds
    ) {}
}
