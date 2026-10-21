package com.arana.payout.payout;

/** Payout-related domain errors. */
class PayoutExceptions {}

/** The payout id does not exist. Maps to HTTP 404. */
class PayoutNotFoundException extends RuntimeException {
    PayoutNotFoundException(String id) {
        super("payout not found: " + id);
    }
}

/** No FX rate configured for the requested currency pair. Maps to HTTP 400. */
class UnsupportedCurrencyException extends RuntimeException {
    UnsupportedCurrencyException(String from, String to) {
        super("no FX rate for " + from + " -> " + to);
    }
}

/** A required header (e.g. Idempotency-Key) was missing. Maps to HTTP 400. */
class MissingHeaderException extends RuntimeException {
    MissingHeaderException(String header) {
        super("missing required header: " + header);
    }
}
