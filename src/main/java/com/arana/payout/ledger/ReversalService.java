package com.arana.payout.ledger;

import com.arana.payout.payout.Payout;
import com.arana.payout.payout.PayoutStatus;
import com.arana.payout.payout.PayoutStore;
import com.arana.payout.persistence.LedgerEntryEntity;
import com.arana.payout.persistence.LedgerEntryJpaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Reverses a payout: posts a PAYOUT_REVERSAL that mirrors every prior line for that
 * payout, which returns the client's funds (and undoes fee/bank movements), then marks
 * the payout RETURNED.
 *
 * Works whether the payout is still PROCESSING (cancel before settlement) or already
 * COMPLETED (a return/bounce after settlement) — it simply mirrors whatever was posted.
 */
@Service
public class ReversalService {

    private static final Logger log = LoggerFactory.getLogger(ReversalService.class);

    private final PayoutStore store;
    private final LedgerService ledger;
    private final LedgerEntryJpaRepository entries;

    public ReversalService(PayoutStore store, LedgerService ledger, LedgerEntryJpaRepository entries) {
        this.store = store;
        this.ledger = ledger;
        this.entries = entries;
    }

    @Transactional
    public Payout reverse(String payoutId) {
        Payout p = store.findById(payoutId).orElse(null);
        if (p == null) {
            return null; // caller maps to 404
        }
        if (p.status() == PayoutStatus.RETURNED) {
            throw new ReversalNotAllowedException("payout already reversed: " + payoutId);
        }
        if (p.status() == PayoutStatus.FAILED) {
            throw new ReversalNotAllowedException("payout failed and has nothing to reverse: " + payoutId);
        }

        // Mirror every non-reversal line posted for this payout.
        List<LedgerEntryEntity> originals = entries.findByReferenceOrderByCreatedAtAscIdAsc(payoutId).stream()
                .filter(e -> !"PAYOUT_REVERSAL".equals(e.getTxnType()))
                .toList();
        if (originals.isEmpty()) {
            throw new ReversalNotAllowedException("no ledger entries to reverse for " + payoutId);
        }

        ledger.postReversal(payoutId, originals);
        p.markReturned();
        store.save(p);
        log.info("reversed payout {} (status -> RETURNED)", payoutId);
        return p;
    }
}
