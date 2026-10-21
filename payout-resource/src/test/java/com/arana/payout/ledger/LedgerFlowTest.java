package com.arana.payout.ledger;

import com.arana.payout.payout.Payout;
import com.arana.payout.payout.PayoutDtos.Amount;
import com.arana.payout.payout.PayoutDtos.Bank;
import com.arana.payout.payout.PayoutDtos.CreatePayoutRequest;
import com.arana.payout.payout.PayoutDtos.Recipient;
import com.arana.payout.payout.PayoutService;
import com.arana.payout.payout.PayoutStatus;
import com.arana.payout.persistence.LedgerEntryEntity;
import com.arana.payout.persistence.LedgerEntryJpaRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Exercises the full money flow against an in-memory H2 database and asserts the
 * one invariant that matters: the ledger always balances (sum debits == sum credits).
 */
@SpringBootTest
class LedgerFlowTest {

    @Autowired PayoutService service;
    @Autowired LedgerService ledger;
    @Autowired SettlementProcessor settlement;
    @Autowired ReversalService reversal;
    @Autowired ReconciliationJob reconciliationJob;
    @Autowired LedgerEntryJpaRepository entries;

    private static final String CLIENT = "demo-client";
    private static final String FUNDS = "CLIENT_FUNDS:demo-client";

    private CreatePayoutRequest request(String amount) {
        return new CreatePayoutRequest(
                new Amount(new BigDecimal(amount), "USD"),
                "PEN",
                new Recipient("Jose Arana", new Bank("PE", "0011-0123", "BCP", "checking")),
                "inv-1",
                "services");
    }

    @Test
    void fullFlow_keepsLedgerBalanced() {
        // Seeded on boot.
        assertThat(ledger.availableBalance(FUNDS)).isEqualByComparingTo("100000.00");
        assertLedgerBalanced();

        // Create a payout: 1500 gross + 15 fee (1%) debited from the client's balance.
        Payout p = service.create(CLIENT, "key-1", request("1500.00"));
        assertThat(p.status()).isEqualTo(PayoutStatus.PROCESSING);
        assertThat(ledger.availableBalance(FUNDS)).isEqualByComparingTo("98485.00"); // 100000 - 1515
        assertLedgerBalanced();

        // The commit posted 3 journal lines for this payout.
        assertThat(ledger.entriesForReference(p.id())).hasSize(3);

        // The fast snapshot matches the authoritative sum-of-journal.
        assertThat(ledger.snapshotBalance(FUNDS)).isEqualByComparingTo(ledger.availableBalance(FUNDS));

        // Idempotency: same key returns the SAME payout, no extra debit.
        Payout again = service.create(CLIENT, "key-1", request("1500.00"));
        assertThat(again.id()).isEqualTo(p.id());
        assertThat(ledger.availableBalance(FUNDS)).isEqualByComparingTo("98485.00");

        // Settlement: money leaves the bank; in-transit clears; ledger still balances.
        Payout settled = settlement.settle(p.id());
        assertThat(settled).isNotNull();
        assertThat(settled.status()).isEqualTo(PayoutStatus.COMPLETED);
        assertThat(ledger.availableBalance("PAYOUTS_IN_TRANSIT")).isEqualByComparingTo("0.00");
        assertLedgerBalanced();

        // Now 5 lines for this payout: 3 from commit + 2 from settlement.
        assertThat(ledger.entriesForReference(p.id())).hasSize(5);

        // Settling again is a safe no-op.
        assertThat(settlement.settle(p.id())).isNull();

        // Reverse/refund: funds return to the client, status RETURNED, ledger still balances.
        Payout reversed = reversal.reverse(p.id());
        assertThat(reversed.status()).isEqualTo(PayoutStatus.RETURNED);
        assertThat(ledger.availableBalance(FUNDS)).isEqualByComparingTo("100000.00"); // whole again
        assertThat(ledger.entriesForReference(p.id())).hasSize(10); // 3 commit + 2 settle + 5 reversal
        assertLedgerBalanced();

        // Reversing again is not allowed.
        assertThatThrownBy(() -> reversal.reverse(p.id()))
                .isInstanceOf(ReversalNotAllowedException.class);

        // Reconciliation: every cached snapshot matches the journal re-sum.
        assertThat(ledger.reconcileAll()).allMatch(ReconItem::matches);

        // Statement: the client's funds account has 3 lines (funding, commit, reversal), newest first.
        LedgerPage stmt = ledger.statement(FUNDS, null, 0, 20);
        assertThat(stmt.content()).hasSize(3);
        assertThat(stmt.totalElements()).isEqualTo(3);
        assertThat(stmt.content().get(0).txnType()).isEqualTo("PAYOUT_REVERSAL"); // most recent

        // Filter by type.
        assertThat(ledger.statement(FUNDS, "PAYOUT_COMMIT", 0, 20).content()).hasSize(1);

        // The nightly job reports zero mismatches on a healthy ledger.
        assertThat(reconciliationJob.checkAndReport()).isEmpty();

        printLedger(p.id());
    }

    private void printLedger(String payoutId) {
        System.out.println("\n===== LEDGER FOR " + payoutId + " =====");
        System.out.printf("%-16s %-24s %12s %12s  %s%n", "TXN_TYPE", "ACCOUNT", "DEBIT", "CREDIT", "CCY");
        for (LedgerEntryEntity e : entries.findByReferenceOrderByCreatedAtAscIdAsc(payoutId)) {
            System.out.printf("%-16s %-24s %12s %12s  %s%n",
                    e.getTxnType(), e.getAccountId(), e.getDebit(), e.getCredit(), e.getCurrency());
        }
        System.out.println("=====================================\n");
    }

    @Test
    void payoutBeyondBalance_isRejected() {
        assertThatThrownBy(() -> service.create(CLIENT, "key-big", request("999999999.00")))
                .isInstanceOf(InsufficientFundsException.class);
    }

    private void assertLedgerBalanced() {
        BigDecimal debits = BigDecimal.ZERO;
        BigDecimal credits = BigDecimal.ZERO;
        for (LedgerEntryEntity e : entries.findAll()) {
            debits = debits.add(e.getDebit());
            credits = credits.add(e.getCredit());
        }
        assertThat(debits).isEqualByComparingTo(credits);
    }
}
