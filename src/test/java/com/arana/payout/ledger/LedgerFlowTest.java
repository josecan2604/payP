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
