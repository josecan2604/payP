package com.arana.payout.payout;

/**
 * The lifecycle of a payout. A real integration must handle every branch:
 * money movement is asynchronous, so you never get a final answer in the create call.
 */
public enum PayoutStatus {
    PROCESSING,   // accepted, settlement in flight
    COMPLETED,    // funds delivered to the recipient bank
    FAILED,       // could not be sent (e.g. provider error)
    RETURNED      // sent but bounced back (e.g. wrong account)
}
