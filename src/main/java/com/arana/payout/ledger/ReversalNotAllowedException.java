package com.arana.payout.ledger;

/** The payout is in a state that can't be reversed (already returned, or failed). Maps to HTTP 409. */
public class ReversalNotAllowedException extends RuntimeException {
    public ReversalNotAllowedException(String message) {
        super(message);
    }
}
