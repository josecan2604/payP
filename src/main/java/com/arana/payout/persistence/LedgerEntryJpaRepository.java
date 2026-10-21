package com.arana.payout.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;

public interface LedgerEntryJpaRepository extends JpaRepository<LedgerEntryEntity, String> {

    /** All journal lines for a business reference (e.g. a payout id), oldest first. */
    List<LedgerEntryEntity> findByReferenceOrderByCreatedAtAscIdAsc(String reference);

    /**
     * Credit-normal balance of an account: sum(credit) - sum(debit).
     * Positive for a liability/income account that holds value (e.g. client funds).
     */
    @Query("select coalesce(sum(e.credit), 0) - coalesce(sum(e.debit), 0) " +
           "from LedgerEntryEntity e where e.accountId = :accountId")
    BigDecimal creditMinusDebit(@Param("accountId") String accountId);

    long countByAccountId(String accountId);
}
