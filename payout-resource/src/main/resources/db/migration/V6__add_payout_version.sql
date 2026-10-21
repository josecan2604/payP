-- Optimistic-lock version for the payout status machine. A concurrent settle vs reverse
-- from a stale read is rejected at write time (JPA bumps and checks this column).
ALTER TABLE payout ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
