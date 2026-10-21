package com.arana.payout.persistence;

import java.math.BigDecimal;

/** A computed (from-journal) balance for one account, used by reconciliation. */
public interface AccountBalanceProjection {
    String getAccountId();
    BigDecimal getBalance();
}
