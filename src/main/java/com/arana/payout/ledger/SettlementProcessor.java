package com.arana.payout.ledger;

import com.arana.payout.payout.Payout;
import com.arana.payout.payout.PayoutStatus;
import com.arana.payout.payout.PayoutStore;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Settles a payout: flips it to COMPLETED and posts the settlement journal.
 *
 * Settlement entry (source currency, gross G):
 *   DEBIT  PAYOUTS_IN_TRANSIT  G   (we no longer owe it to the pipeline)
 *   CREDIT BANK_CASH           G   (real money has now left our bank)
 *
 * Runs in its own transaction so the status change and the ledger posting commit
 * together, or not at all.
 */
@Service
public class SettlementProcessor {

    private final PayoutStore store;
    private final LedgerService ledger;
    private final java.time.Clock clock;

    public SettlementProcessor(PayoutStore store, LedgerService ledger, java.time.Clock clock) {
        this.store = store;
        this.ledger = ledger;
        this.clock = clock;
    }

    @Transactional
    public Payout settle(String payoutId) {
        Payout p = store.findById(payoutId).orElse(null);
        if (p == null || p.status() != PayoutStatus.PROCESSING) {
            return null; // already settled or gone — nothing to do (safe to call twice)
        }

        String ccy = p.amount().currency();
        var gross = p.amount().value();

        ledger.post("PAYOUT_SETTLE", List.of(
                JournalLine.debit(LedgerAccounts.PAYOUTS_IN_TRANSIT, AccountType.LIABILITY, gross, ccy),
                JournalLine.credit(LedgerAccounts.BANK_CASH, AccountType.ASSET, gross, ccy)
        ));

        p.markCompleted(clock.instant());
        store.save(p);
        return p;
    }
}
