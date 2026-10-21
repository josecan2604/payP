package com.arana.payout.ledger;

/**
 * The classic five account families (we use four here). The "type" decides which
 * side is the account's normal balance:
 *   ASSET, EXPENSE      -> normal DEBIT  (increase with a debit)
 *   LIABILITY, INCOME   -> normal CREDIT (increase with a credit)
 */
public enum AccountType {
    ASSET,
    LIABILITY,
    INCOME,
    EXPENSE
}
