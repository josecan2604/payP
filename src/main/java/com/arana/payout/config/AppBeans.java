package com.arana.payout.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

@Configuration
public class AppBeans {

    /** Injected instead of calling Instant.now() directly, so time is testable. */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    /** Used to simulate asynchronous settlement (PROCESSING -> COMPLETED after a delay). */
    @Bean(destroyMethod = "shutdown")
    public ScheduledExecutorService settlementScheduler() {
        return Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "settlement-scheduler");
            t.setDaemon(true);
            return t;
        });
    }

    /** HTTP client used to deliver webhooks. */
    @Bean
    public RestClient restClient() {
        return RestClient.create();
    }
}
