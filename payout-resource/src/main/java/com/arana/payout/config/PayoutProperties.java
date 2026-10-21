package com.arana.payout.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Map;

/**
 * Binds the "payout.*" section of application.yml.
 */
@ConfigurationProperties(prefix = "payout")
public record PayoutProperties(
        Map<String, String> clients,   // client id -> (unused here) ; drives funding on boot
        Webhook webhook,
        long statusFlipDelaySeconds,
        Map<String, Double> fx,
        double seedBalance,
        String seedCurrency
) {
    public record Webhook(
            String secret,
            String callbackUrl,
            long toleranceSeconds
    ) {}
}
