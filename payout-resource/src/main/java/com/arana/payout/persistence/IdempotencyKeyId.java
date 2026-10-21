package com.arana.payout.persistence;

import java.io.Serializable;
import java.util.Objects;

/**
 * Composite primary key for {@link IdempotencyKeyEntity}: the key is scoped PER CLIENT,
 * so the same Idempotency-Key value from two different clients never collides (no cross-tenant leak).
 */
public class IdempotencyKeyId implements Serializable {

    private String clientId;
    private String key;

    public IdempotencyKeyId() {
    }

    public IdempotencyKeyId(String clientId, String key) {
        this.clientId = clientId;
        this.key = key;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof IdempotencyKeyId that)) return false;
        return Objects.equals(clientId, that.clientId) && Objects.equals(key, that.key);
    }

    @Override
    public int hashCode() {
        return Objects.hash(clientId, key);
    }
}
