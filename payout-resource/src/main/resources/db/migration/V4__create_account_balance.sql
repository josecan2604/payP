-- Running-balance snapshot: one row per account, updated in the same transaction as the
-- journal entries. Lets GET /balance read a single row instead of summing the whole journal.
-- The journal (ledger_entry) stays the source of truth; this is a rebuildable cache.
-- Stored value is the credit-normal balance: SUM(credit) - SUM(debit).

CREATE TABLE account_balance (
    account_id  VARCHAR(100)  PRIMARY KEY REFERENCES ledger_account(id),
    balance     NUMERIC(18,2) NOT NULL,
    updated_at  TIMESTAMPTZ   NOT NULL
);
