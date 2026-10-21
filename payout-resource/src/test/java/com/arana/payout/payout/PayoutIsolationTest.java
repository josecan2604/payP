package com.arana.payout.payout;

import com.arana.payout.payout.PayoutDtos.Amount;
import com.arana.payout.payout.PayoutDtos.Bank;
import com.arana.payout.payout.PayoutDtos.CreatePayoutRequest;
import com.arana.payout.payout.PayoutDtos.Recipient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Cross-client isolation. Runs on its own in-memory DB so it can't contaminate other tests.
 * Both demo-client and other-client are seeded with funds (see src/test/resources/application.yml).
 */
@SpringBootTest
@TestPropertySource(properties =
        "spring.datasource.url=jdbc:h2:mem:isolation;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
class PayoutIsolationTest {

    @Autowired PayoutService service;

    private static final String A = "demo-client";
    private static final String B = "other-client";

    private CreatePayoutRequest req(String amount) {
        return new CreatePayoutRequest(
                new Amount(new BigDecimal(amount), "USD"), "PEN",
                new Recipient("Jose Arana", new Bank("PE", "0011-0123", "BCP", "checking")),
                "inv", "services");
    }

    @Test
    void idempotencyKeyIsScopedPerClient_noCrossTenantLeak() {
        // Client A creates a payout with a shared key.
        Payout a = service.create(A, "SHARED-KEY", req("100.00"));

        // Client B uses the SAME key — must get its OWN payout, never A's.
        Payout b = service.create(B, "SHARED-KEY", req("200.00"));
        assertThat(b.id()).isNotEqualTo(a.id());
        assertThat(b.clientId()).isEqualTo(B);
        assertThat(b.amount().value()).isEqualByComparingTo("200.00");

        // A retrying with the same key still gets A's original payout.
        Payout aAgain = service.create(A, "SHARED-KEY", req("100.00"));
        assertThat(aAgain.id()).isEqualTo(a.id());
    }

    @Test
    void clientCannotAccessAnotherClientsPayout() {
        Payout a = service.create(A, "K-iso-1", req("100.00"));

        // B cannot read A's payout, nor its ledger entries -> 404 (PayoutNotFoundException).
        assertThatThrownBy(() -> service.getForClient(B, a.id()))
                .isInstanceOf(PayoutNotFoundException.class);
        assertThatThrownBy(() -> service.entriesForPayout(B, a.id()))
                .isInstanceOf(PayoutNotFoundException.class);

        // The owner still can.
        assertThat(service.getForClient(A, a.id()).id()).isEqualTo(a.id());
    }
}
