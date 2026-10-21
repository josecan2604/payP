package com.arana.payout;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Educational "payments provider" service.
 *
 * It demonstrates, end to end, how a real payout integration is shaped:
 *   1. AUTH     - exchange client credentials for a short-lived bearer token.
 *   2. PAYOUT   - create a payout (async + idempotent), then read its status.
 *   3. WEBHOOK  - when the payout "settles", POST a SIGNED event to a callback URL,
 *                 and verify that signature on the receiving end.
 *
 * Everything is in-memory; no real money moves. The point is the mechanics.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class PayoutDemoApplication {
    public static void main(String[] args) {
        SpringApplication.run(PayoutDemoApplication.class, args);
    }
}
