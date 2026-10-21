package com.arana.payout.ledger;

import java.math.BigDecimal;

/** A client tried to pay out more than their available balance. Maps to HTTP 422. */
public class InsufficientFundsException extends RuntimeException {
    public InsufficientFundsException(String accountId, BigDecimal available, BigDecimal required) {
        super("insufficient funds in " + accountId + ": available=" + available + ", required=" + required);
    }
}
