package com.arana.payout.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Maps a caller-supplied Idempotency-Key to the payout it created, so a retried request
 * returns the original payout instead of creating a duplicate.
 *
 * The key is scoped PER CLIENT (composite PK of client_id + idempotency_key), so the same
 * key value from two different clients is isolated — one client can never retrieve another's
 * payout by reusing its Idempotency-Key.
 */
@Entity
@Table(name = "idempotency_key")
@IdClass(IdempotencyKeyId.class)
public class IdempotencyKeyEntity {

    @Id
    @Column(name = "client_id")
    private String clientId;

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

    public IdempotencyKeyEntity(String clientId, String key, String payoutId, Instant createdAt) {
        this.clientId = clientId;
        this.key = key;
        this.payoutId = payoutId;
        this.createdAt = createdAt;
    }

    public String getClientId() { return clientId; }
    public String getKey() { return key; }
    public String getPayoutId() { return payoutId; }
    public Instant getCreatedAt() { return createdAt; }
}
