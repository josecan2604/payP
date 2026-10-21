package com.arana.payout.payout;

import com.arana.payout.config.PayoutProperties;
import com.arana.payout.payout.PayoutDtos.CreatePayoutRequest;
import com.arana.payout.webhook.WebhookSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Core use case: create a payout and drive it through its lifecycle.
 *
 * Teaching points baked in here:
 *  - IDEMPOTENCY: the same Idempotency-Key never creates two payouts.
 *  - ASYNC: create() returns PROCESSING immediately; settlement happens later
 *           on a scheduler, which then fires the webhook.
 *  - FX + FEE: the applied rate and fee are recorded on the payout (that's where
 *              a provider's margin lives).
 */
@Service
public class PayoutService {

    private static final Logger log = LoggerFactory.getLogger(PayoutService.class);
    private static final BigDecimal FEE_RATE = new BigDecimal("0.01"); // 1% demo fee

    private final PayoutStore store;
    private final PayoutProperties props;
    private final ScheduledExecutorService scheduler;
    private final WebhookSender webhookSender;
    private final Clock clock;

    public PayoutService(PayoutStore store, PayoutProperties props,
                         ScheduledExecutorService settlementScheduler,
                         WebhookSender webhookSender, Clock clock) {
        this.store = store;
        this.props = props;
        this.scheduler = settlementScheduler;
        this.webhookSender = webhookSender;
        this.clock = clock;
    }

    public Payout create(String clientId, String idempotencyKey, CreatePayoutRequest req) {
        // 1. Idempotency: a retried request returns the original payout, never a duplicate.
        var existingId = store.existingForKey(idempotencyKey);
        if (existingId.isPresent()) {
            log.info("idempotency hit for key {} -> payout {}", idempotencyKey, existingId.get());
            return store.findById(existingId.get()).orElseThrow();
        }

        // 2. FX lookup (source -> destination currency).
        String from = req.amount().currency();
        String to = req.payoutCurrency();
        double rate = lookupRate(from, to);

        // 3. Money math with BigDecimal, rounded to 2 decimals.
        BigDecimal gross = req.amount().value();
        BigDecimal feeValue = gross.multiply(FEE_RATE).setScale(2, RoundingMode.HALF_UP);
        BigDecimal payoutValue = gross.multiply(BigDecimal.valueOf(rate)).setScale(2, RoundingMode.HALF_UP);

        Instant now = clock.instant();
        Instant eta = now.plusSeconds(props.statusFlipDelaySeconds());
        String id = "po_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);

        Payout payout = new Payout(
                id, clientId,
                new Money(gross, from),
                new Money(payoutValue, to),
                rate,
                new Money(feeValue, from),
                req.reference(),
                now, eta);

        store.save(payout);
        store.linkKey(idempotencyKey, id);
        log.info("created payout {} ({} {} -> {} {})", id, gross, from, payoutValue, to);

        // 4. Simulate asynchronous settlement, then fire the webhook.
        scheduleSettlement(id);
        return payout;
    }

    public Payout get(String id) {
        return store.findById(id)
                .orElseThrow(() -> new PayoutNotFoundException(id));
    }

    private void scheduleSettlement(String id) {
        scheduler.schedule(() -> {
            store.findById(id).ifPresent(p -> {
                p.markCompleted(clock.instant());
                store.save(p);
                log.info("payout {} settled -> COMPLETED", id);
                webhookSender.sendPayoutEvent("payout.completed", p);
            });
        }, props.statusFlipDelaySeconds(), TimeUnit.SECONDS);
    }

    private double lookupRate(String from, String to) {
        if (from.equals(to)) {
            return 1.0;
        }
        Double rate = props.fx().get(from + "_" + to);
        if (rate == null) {
            throw new UnsupportedCurrencyException(from, to);
        }
        return rate;
    }
}
