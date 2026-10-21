-- Scope the idempotency key PER CLIENT so the same key from two clients never collides.
-- (Previously the key alone was the PK, which let one client retrieve another's payout.)

ALTER TABLE idempotency_key ADD COLUMN client_id VARCHAR(100);

-- Backfill existing rows from the payout they created.
UPDATE idempotency_key k
   SET client_id = (SELECT p.client_id FROM payout p WHERE p.id = k.payout_id)
 WHERE client_id IS NULL;

ALTER TABLE idempotency_key ALTER COLUMN client_id SET NOT NULL;

-- Replace the single-column PK with a composite (client_id, idempotency_key).
ALTER TABLE idempotency_key DROP CONSTRAINT idempotency_key_pkey;
ALTER TABLE idempotency_key ADD PRIMARY KEY (client_id, idempotency_key);
