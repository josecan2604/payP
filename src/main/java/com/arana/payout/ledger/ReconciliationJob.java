package com.arana.payout.ledger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Nightly safety net: re-sums the journal per account and compares it to the cached
 * snapshots. In a healthy system every account matches; a mismatch means the cache has
 * drifted from the source of truth and must be investigated/rebuilt.
 *
 * Here a mismatch is logged at ERROR; in production this is where you'd page on-call /
 * open an incident / auto-rebuild the snapshot from the journal.
 */
@Component
public class ReconciliationJob {

    private static final Logger log = LoggerFactory.getLogger(ReconciliationJob.class);

    private final LedgerService ledger;

    public ReconciliationJob(LedgerService ledger) {
        this.ledger = ledger;
    }

    /** Runs on the configured schedule (default 03:00 daily). */
    @Scheduled(cron = "${payout.reconciliation.cron:0 0 3 * * *}",
               zone = "${payout.reconciliation.zone:UTC}")
    public void scheduled() {
        checkAndReport();
    }

    /** Core logic, separated so it can be invoked and tested directly. Returns the mismatches. */
    public List<ReconItem> checkAndReport() {
        List<ReconItem> all = ledger.reconcileAll();
        List<ReconItem> mismatches = all.stream().filter(i -> !i.matches()).toList();
        if (mismatches.isEmpty()) {
            log.info("reconciliation OK: {} account(s) balanced", all.size());
        } else {
            for (ReconItem m : mismatches) {
                log.error("RECONCILIATION MISMATCH account={} snapshot={} computed={}",
                        m.account(), m.snapshot(), m.computed());
            }
            // production: alert / page on-call / open incident / rebuild snapshot from journal
        }
        return mismatches;
    }
}
