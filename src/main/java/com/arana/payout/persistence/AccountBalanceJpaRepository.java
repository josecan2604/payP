package com.arana.payout.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;

public interface AccountBalanceJpaRepository extends JpaRepository<AccountBalanceEntity, String> {

    /**
     * Atomically add a delta to an account's cached balance. Returns the number of rows
     * updated (0 means the account has no snapshot row yet, so the caller inserts one).
     * Doing it as a single UPDATE keeps concurrent postings to the same account correct.
     */
    @Modifying
    @Query("update AccountBalanceEntity b set b.balance = b.balance + :delta, b.updatedAt = :now " +
           "where b.accountId = :id")
    int addToBalance(@Param("id") String id, @Param("delta") BigDecimal delta, @Param("now") Instant now);
}
