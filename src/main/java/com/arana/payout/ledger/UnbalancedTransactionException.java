package com.arana.payout.ledger;

/**
 * A journal transaction whose debits don't equal its credits. This should never
 * happen from valid code paths — it's a programming error, so it maps to HTTP 500.
 */
public class UnbalancedTransactionException extends RuntimeException {
    public UnbalancedTransactionException(String message) {
        super(message);
    }
}
