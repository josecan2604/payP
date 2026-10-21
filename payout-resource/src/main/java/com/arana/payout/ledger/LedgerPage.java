package com.arana.payout.ledger;

import java.util.List;

/** A page of ledger entries plus pagination metadata. */
public record LedgerPage(
        List<LedgerEntryView> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {}
