package com.arana.payout.payout;

import com.arana.payout.persistence.IdempotencyKeyEntity;
import com.arana.payout.persistence.IdempotencyKeyJpaRepository;
import com.arana.payout.persistence.PayoutEntity;
import com.arana.payout.persistence.PayoutJpaRepository;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.Optional;

/**
 * Persistence gateway for payouts, backed by Postgres (via JPA). It maps between
 * the domain {@link Payout} and the JPA {@link PayoutEntity}, so the service layer
 * stays free of persistence concerns.
 */
@Repository
public class PayoutStore {

    private final PayoutJpaRepository payouts;
    private final IdempotencyKeyJpaRepository keys;
    private final Clock clock;

    public PayoutStore(PayoutJpaRepository payouts, IdempotencyKeyJpaRepository keys, Clock clock) {
        this.payouts = payouts;
        this.keys = keys;
        this.clock = clock;
    }

    public void save(Payout payout) {
        payouts.save(toEntity(payout));
    }

    public Optional<Payout> findById(String id) {
        return payouts.findById(id).map(this::toDomain);
    }

    /** Look up a prior payout for this client's idempotency key (scoped per client). */
    public Optional<String> existingForKey(String clientId, String idempotencyKey) {
        return keys.findByClientIdAndKey(clientId, idempotencyKey).map(IdempotencyKeyEntity::getPayoutId);
    }

    public void linkKey(String clientId, String idempotencyKey, String payoutId) {
        keys.save(new IdempotencyKeyEntity(clientId, idempotencyKey, payoutId, clock.instant()));
    }

    // --- mapping -------------------------------------------------------------

    private PayoutEntity toEntity(Payout p) {
        PayoutEntity e = new PayoutEntity();
        e.setId(p.id());
        e.setClientId(p.clientId());
        e.setAmountValue(p.amount().value());
        e.setAmountCurrency(p.amount().currency());
        e.setPayoutValue(p.payout().value());
        e.setPayoutCurrency(p.payout().currency());
        e.setFxRate(BigDecimal.valueOf(p.fxRate()));
        e.setFeeValue(p.fee().value());
        e.setFeeCurrency(p.fee().currency());
        e.setStatus(p.status());
        e.setVersion(p.version());   // carry the version so JPA's optimistic check uses it
        e.setReference(p.reference());
        e.setCreatedAt(p.createdAt());
        e.setEstimatedArrival(p.estimatedArrival());
        e.setCompletedAt(p.completedAt());
        return e;
    }

    private Payout toDomain(PayoutEntity e) {
        return Payout.rehydrate(
                e.getId(),
                e.getClientId(),
                new Money(e.getAmountValue(), e.getAmountCurrency()),
                new Money(e.getPayoutValue(), e.getPayoutCurrency()),
                e.getFxRate().doubleValue(),
                new Money(e.getFeeValue(), e.getFeeCurrency()),
                e.getReference(),
                e.getCreatedAt(),
                e.getEstimatedArrival(),
                e.getStatus(),
                e.getCompletedAt(),
                e.getVersion());
    }
}
