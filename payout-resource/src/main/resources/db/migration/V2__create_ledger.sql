-- Double-entry ledger. Entries are append-only; corrections are reversing entries.

CREATE TABLE ledger_account (
    id          VARCHAR(100) PRIMARY KEY,   -- e.g. BANK_CASH, CLIENT_FUNDS:demo-client
    type        VARCHAR(20)  NOT NULL,      -- ASSET | LIABILITY | INCOME | EXPENSE
    currency    VARCHAR(3)   NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL
);

CREATE TABLE ledger_entry (
    id          VARCHAR(40)   PRIMARY KEY,
    txn_id      VARCHAR(40)   NOT NULL,      -- groups the lines of one balanced transaction
    txn_type    VARCHAR(40)   NOT NULL,      -- FUNDING | PAYOUT_COMMIT | PAYOUT_SETTLE
    account_id  VARCHAR(100)  NOT NULL REFERENCES ledger_account(id),
    currency    VARCHAR(3)    NOT NULL,
    debit       NUMERIC(18,2) NOT NULL DEFAULT 0,
    credit      NUMERIC(18,2) NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ   NOT NULL,
    CONSTRAINT chk_one_side CHECK (
        (debit = 0 AND credit > 0) OR (credit = 0 AND debit > 0)
    )
);

CREATE INDEX idx_ledger_entry_account ON ledger_entry(account_id);
CREATE INDEX idx_ledger_entry_txn     ON ledger_entry(txn_id);
