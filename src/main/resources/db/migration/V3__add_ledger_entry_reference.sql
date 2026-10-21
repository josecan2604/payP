-- Link each journal line to the business object that caused it (e.g. a payout id),
-- so we can list all entries for one payout.
ALTER TABLE ledger_entry ADD COLUMN reference VARCHAR(100);

CREATE INDEX idx_ledger_entry_reference ON ledger_entry(reference);
