package com.arana.payout.payout;

import java.time.Instant;

/**
 * The provider's internal record of one payout — a single row in its "ledger".
 * Mutable only in its status/timing as it settles.
 */
public class Payout {

    private final String id;
    private final String clientId;       // which API client created it
    private final Money amount;          // debited amount (source currency)
    private final Money payout;          // delivered amount (destination currency)
    private final double fxRate;
    private final Money fee;
    private final String reference;
    private final Instant createdAt;
    private final Instant estimatedArrival;

    private volatile PayoutStatus status;
    private volatile Instant completedAt;
    private final long version;           // optimistic-lock version carried from persistence

    public Payout(String id, String clientId, Money amount, Money payout, double fxRate,
                  Money fee, String reference, Instant createdAt, Instant estimatedArrival) {
        this(id, clientId, amount, payout, fxRate, fee, reference, createdAt, estimatedArrival,
                PayoutStatus.PROCESSING, null, 0L);
    }

    private Payout(String id, String clientId, Money amount, Money payout, double fxRate,
                   Money fee, String reference, Instant createdAt, Instant estimatedArrival,
                   PayoutStatus status, Instant completedAt, long version) {
        this.id = id;
        this.clientId = clientId;
        this.amount = amount;
        this.payout = payout;
        this.fxRate = fxRate;
        this.fee = fee;
        this.reference = reference;
        this.createdAt = createdAt;
        this.estimatedArrival = estimatedArrival;
        this.status = status;
        this.completedAt = completedAt;
        this.version = version;
    }

    /** Rebuilds a payout from persisted state (restores status, completedAt and version). */
    public static Payout rehydrate(String id, String clientId, Money amount, Money payout, double fxRate,
                                   Money fee, String reference, Instant createdAt, Instant estimatedArrival,
                                   PayoutStatus status, Instant completedAt, long version) {
        return new Payout(id, clientId, amount, payout, fxRate, fee, reference, createdAt, estimatedArrival,
                status, completedAt, version);
    }

    public void markCompleted(Instant when) {
        this.status = PayoutStatus.COMPLETED;
        this.completedAt = when;
    }

    /** Mark a payout as reversed/returned (funds given back to the client). */
    public void markReturned() {
        this.status = PayoutStatus.RETURNED;
    }

    public String id() { return id; }
    public String clientId() { return clientId; }
    public Money amount() { return amount; }
    public Money payout() { return payout; }
    public double fxRate() { return fxRate; }
    public Money fee() { return fee; }
    public String reference() { return reference; }
    public Instant createdAt() { return createdAt; }
    public Instant estimatedArrival() { return estimatedArrival; }
    public PayoutStatus status() { return status; }
    public Instant completedAt() { return completedAt; }
    public long version() { return version; }
}
