package com.arana.payout.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface IdempotencyKeyJpaRepository extends JpaRepository<IdempotencyKeyEntity, IdempotencyKeyId> {

    /** Look up a key WITHIN a client's scope, so keys never collide across clients. */
    Optional<IdempotencyKeyEntity> findByClientIdAndKey(String clientId, String key);
}
