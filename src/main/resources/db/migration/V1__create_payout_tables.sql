-- Schema for the payout provider demo. Owned by Flyway; Hibernate only validates against it.

CREATE TABLE payout (
    id                 VARCHAR(40)   PRIMARY KEY,
    client_id          VARCHAR(100)  NOT NULL,
    amount_value       NUMERIC(18,2) NOT NULL,
    amount_currency    VARCHAR(3)    NOT NULL,
    payout_value       NUMERIC(18,2) NOT NULL,
    payout_currency    VARCHAR(3)    NOT NULL,
    fx_rate            NUMERIC(18,6) NOT NULL,
    fee_value          NUMERIC(18,2) NOT NULL,
    fee_currency       VARCHAR(3)    NOT NULL,
    status             VARCHAR(20)   NOT NULL,
    reference          VARCHAR(255),
    created_at         TIMESTAMPTZ   NOT NULL,
    estimated_arrival  TIMESTAMPTZ   NOT NULL,
    completed_at       TIMESTAMPTZ
);

CREATE TABLE idempotency_key (
    idempotency_key  VARCHAR(255) PRIMARY KEY,
    payout_id        VARCHAR(40)  NOT NULL REFERENCES payout(id),
    created_at       TIMESTAMPTZ  NOT NULL
);

CREATE INDEX idx_payout_client_id ON payout(client_id);
CREATE INDEX idx_payout_status    ON payout(status);
