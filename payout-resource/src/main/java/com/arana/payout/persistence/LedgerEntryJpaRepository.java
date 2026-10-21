package com.arana.payout.persistence;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;

public interface LedgerEntryJpaRepository extends JpaRepository<LedgerEntryEntity, String> {

    /** All journal lines for a business reference (e.g. a payout id), oldest first. */
    List<LedgerEntryEntity> findByReferenceOrderByCreatedAtAscIdAsc(String reference);

    /** A page of an account's entries (ordering comes from the Pageable). */
    Page<LedgerEntryEntity> findByAccountId(String accountId, Pageable pageable);

    /** A page of an account's entries filtered by transaction type. */
    Page<LedgerEntryEntity> findByAccountIdAndTxnType(String accountId, String txnType, Pageable pageable);

    /**
     * Credit-normal balance of an account: sum(credit) - sum(debit).
     * Positive for a liability/income account that holds value (e.g. client funds).
     */
    @Query("select coalesce(sum(e.credit), 0) - coalesce(sum(e.debit), 0) " +
           "from LedgerEntryEntity e where e.accountId = :accountId")
    BigDecimal creditMinusDebit(@Param("accountId") String accountId);

    long countByAccountId(String accountId);

    /** Credit-normal balance per account, computed straight from the journal (for reconciliation). */
    @Query("select e.accountId as accountId, " +
           "coalesce(sum(e.credit), 0) - coalesce(sum(e.debit), 0) as balance " +
           "from LedgerEntryEntity e group by e.accountId")
    List<AccountBalanceProjection> computeAllBalances();
}
