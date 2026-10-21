package com.arana.payout.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

public interface LedgerAccountJpaRepository extends JpaRepository<LedgerAccountEntity, String> {
}
