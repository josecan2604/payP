package com.arana.payout.ledger;

import com.arana.payout.persistence.LedgerEntryEntity;

import java.math.BigDecimal;
import java.time.Instant;

/** Read model for one journal line, returned by the entries endpoint. */
public record LedgerEntryView(
        String txnId,
        String txnType,
        String account,
        BigDecimal debit,
        BigDecimal credit,
        String currency,
        Instant createdAt
) {
    public static LedgerEntryView from(LedgerEntryEntity e) {
        return new LedgerEntryView(
                e.getTxnId(), e.getTxnType(), e.getAccountId(),
                e.getDebit(), e.getCredit(), e.getCurrency(), e.getCreatedAt());
    }
}
