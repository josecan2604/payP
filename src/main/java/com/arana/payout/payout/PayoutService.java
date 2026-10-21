package com.arana.payout.payout;

import com.arana.payout.config.PayoutProperties;
import com.arana.payout.ledger.AccountType;
import com.arana.payout.ledger.InsufficientFundsException;
import com.arana.payout.ledger.JournalLine;
import com.arana.payout.ledger.LedgerAccounts;
import com.arana.payout.ledger.LedgerService;
import com.arana.payout.ledger.SettlementProcessor;
import com.arana.payout.payout.PayoutDtos.CreatePayoutRequest;
import com.arana.payout.webhook.WebhookSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Core use case: create a payout and drive it through its lifecycle, with every
 * money movement recorded in the double-entry ledger.
 *
 *  - IDEMPOTENCY: the same Idempotency-Key never creates two payouts.
 *  - FUNDS CHECK: a client can't pay out more than their available balance.
 *  - LEDGER (commit): on create we move money off the client's balance:
 *        DEBIT  CLIENT_FUNDS:<client>  (gross + fee)
 *        CREDIT PAYOUTS_IN_TRANSIT     (gross)
 *        CREDIT FEE_INCOME             (fee)
 *  - ASYNC: create() returns PROCESSING; settlement (and its ledger entry) runs later.
 *
 * Note: the ledger here runs in the SOURCE currency. Real multi-currency ledgers add
 * per-currency accounts plus an FX gain/loss account; that's intentionally out of scope.
 */
@Service
public class PayoutService {

    private static final Logger log = LoggerFactory.getLogger(PayoutService.class);
    private static final BigDecimal FEE_RATE = new BigDecimal("0.01"); // 1% demo fee

    private final PayoutStore store;
    private final PayoutProperties props;
    private final ScheduledExecutorService scheduler;
    private final WebhookSender webhookSender;
    private final LedgerService ledger;
    private final SettlementProcessor settlement;
    private final Clock clock;

    public PayoutService(PayoutStore store, PayoutProperties props,
                         ScheduledExecutorService settlementScheduler, WebhookSender webhookSender,
                         LedgerService ledger, SettlementProcessor settlement, Clock clock) {
        this.store = store;
        this.props = props;
        this.scheduler = settlementScheduler;
        this.webhookSender = webhookSender;
        this.ledger = ledger;
        this.settlement = settlement;
        this.clock = clock;
    }

    @Transactional
    public Payout create(String clientId, String idempotencyKey, CreatePayoutRequest req) {
        // 1. Idempotency: a retried request returns the original payout, never a duplicate.
        var existingId = store.existingForKey(idempotencyKey);
        if (existingId.isPresent()) {
            log.info("idempotency hit for key {} -> payout {}", idempotencyKey, existingId.get());
            return store.findById(existingId.get()).orElseThrow();
        }

        // 2. FX + money math (2 decimals, BigDecimal).
        String from = req.amount().currency();
        String to = req.payoutCurrency();
        double rate = lookupRate(from, to);
        BigDecimal gross = req.amount().value().setScale(2, RoundingMode.HALF_UP);
        BigDecimal fee = gross.multiply(FEE_RATE).setScale(2, RoundingMode.HALF_UP);
        BigDecimal payoutValue = gross.multiply(BigDecimal.valueOf(rate)).setScale(2, RoundingMode.HALF_UP);
        BigDecimal required = gross.add(fee); // debited from the client's balance

        // 3. Funds check against the ledger.
        String fundsAccount = LedgerAccounts.clientFunds(clientId);
        BigDecimal available = ledger.availableBalance(fundsAccount);
        if (available.compareTo(required) < 0) {
            throw new InsufficientFundsException(fundsAccount, available, required);
        }

        // 4. Create the payout record (PROCESSING).
        Instant now = clock.instant();
        Instant eta = now.plusSeconds(props.statusFlipDelaySeconds());
        String id = "po_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        Payout payout = new Payout(
                id, clientId,
                new Money(gross, from),
                new Money(payoutValue, to),
                rate,
                new Money(fee, from),
                req.reference(),
                now, eta);
        store.save(payout);
        store.linkKey(idempotencyKey, id);

        // 5. Ledger commit: move money off the client's balance (balanced transaction).
        ledger.post("PAYOUT_COMMIT", List.of(
                JournalLine.debit(fundsAccount, AccountType.LIABILITY, required, from),
                JournalLine.credit(LedgerAccounts.PAYOUTS_IN_TRANSIT, AccountType.LIABILITY, gross, from),
                JournalLine.credit(LedgerAccounts.FEE_INCOME, AccountType.INCOME, fee, from)
        ));
        log.info("created payout {} ({} {} -> {} {}), fee {} {}", id, gross, from, payoutValue, to, fee, from);

        // 6. Settle asynchronously, then fire the webhook once settled & committed.
        scheduleSettlement(id);
        return payout;
    }

    public Payout get(String id) {
        return store.findById(id).orElseThrow(() -> new PayoutNotFoundException(id));
    }

    private void scheduleSettlement(String id) {
        scheduler.schedule(() -> {
            try {
                Payout settled = settlement.settle(id); // own @Transactional
                if (settled != null) {
                    webhookSender.sendPayoutEvent("payout.completed", settled);
                }
            } catch (Exception e) {
                log.error("settlement failed for payout {}", id, e);
            }
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
