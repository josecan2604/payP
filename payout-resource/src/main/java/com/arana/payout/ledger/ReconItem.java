package com.arana.payout.ledger;

import java.math.BigDecimal;

/**
 * One account's reconciliation result: the cached snapshot vs the balance recomputed
 * from the journal. {@code matches} must be true for every account in a healthy system.
 */
public record ReconItem(String account, BigDecimal snapshot, BigDecimal computed, boolean matches) {}
