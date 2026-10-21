package com.arana.payout.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Maps a caller-supplied Idempotency-Key to the payout it created, so a retried
 * request returns the original payout instead of creating a duplicate.
 */
@Entity
@Table(name = "idempotency_key")
public class IdempotencyKeyEntity {

    @Id
    @Column(name = "idempotency_key")
    private String key;

    @Column(name = "payout_id", nullable = false)
    private String payoutId;

    @Column(nullable = false)
    private Instant createdAt;

    protected IdempotencyKeyEntity() {
        // required by JPA
    }

    public IdempotencyKeyEntity(String key, String payoutId, Instant createdAt) {
        this.key = key;
        this.payoutId = payoutId;
        this.createdAt = createdAt;
    }

    public String getKey() { return key; }
    public String getPayoutId() { return payoutId; }
    public Instant getCreatedAt() { return createdAt; }
}
