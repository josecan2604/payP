package com.arana.payout.ledger;

import java.math.BigDecimal;

/**
 * One line of a journal entry: "this account, this side, this amount".
 * A balanced transaction is a list of these whose debits equal its credits.
 */
public record JournalLine(
        String accountId,
        AccountType accountType,
        Direction direction,
        BigDecimal amount,
        String currency
) {
    public static JournalLine debit(String accountId, AccountType type, BigDecimal amount, String currency) {
        return new JournalLine(accountId, type, Direction.DEBIT, amount, currency);
    }

    public static JournalLine credit(String accountId, AccountType type, BigDecimal amount, String currency) {
        return new JournalLine(accountId, type, Direction.CREDIT, amount, currency);
    }
}
