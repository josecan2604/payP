package com.arana.payout.ledger;

/**
 * The chart of accounts for this demo provider.
 *
 *   BANK_CASH           (ASSET)     - real money we hold at our bank
 *   CLIENT_FUNDS:<id>   (LIABILITY) - money we owe a given client (their balance)
 *   PAYOUTS_IN_TRANSIT  (LIABILITY) - committed payouts not yet settled
 *   FEE_INCOME          (INCOME)    - fees we have earned
 */
public final class LedgerAccounts {

    private LedgerAccounts() {}

    public static final String BANK_CASH = "BANK_CASH";
    public static final String PAYOUTS_IN_TRANSIT = "PAYOUTS_IN_TRANSIT";
    public static final String FEE_INCOME = "FEE_INCOME";

    public static String clientFunds(String clientId) {
        return "CLIENT_FUNDS:" + clientId;
    }
}
