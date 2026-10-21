package com.arana.payout.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PayoutJpaRepository extends JpaRepository<PayoutEntity, String> {
}
