# payP — payout provider demo

An educational Spring Boot service that shows, end to end, how a real **payout
integration** is shaped — the same mechanics a provider like Payoneer/Stripe/Wise
exposes:

1. **Auth** — exchange client credentials for a short-lived bearer token.
2. **Payout API** — create a payout (**async** + **idempotent**), then read its status.
3. **Webhooks** — when the payout "settles", the provider POSTs a **signed** event to a
   callback URL; the receiver **verifies the HMAC signature** before trusting it.

No real money moves — the point is the mechanics.

## Stack
- Java 21, Spring Boot 3.5 (web + validation; no Lombok)
- **Postgres** via Spring Data JPA; schema owned by **Flyway** (`ddl-auto=validate`)
- HMAC-SHA256 webhook signatures
- RFC 9457 `ProblemDetail` error responses

## Run
```bash
# 1. start Postgres (schema is created by Flyway on first boot)
docker compose up -d

# 2. run the app  (override DB via DB_URL / DB_USER / DB_PASSWORD if needed)
mvn spring-boot:run
# listens on http://localhost:8090
```

### Schema (owned by Flyway — `src/main/resources/db/migration`)
- `payout` — the ledger: one row per payout (amount, payout, fx_rate, fee, status, timestamps)
- `idempotency_key` — maps an Idempotency-Key to the payout it created, so retries don't duplicate

## Walk through the flow

### 1. Get a token
```bash
curl -s -X POST http://localhost:8090/oauth/token \
  -H "Content-Type: application/json" \
  -d '{"grant_type":"client_credentials","client_id":"demo-client","client_secret":"demo-secret"}'
```
Returns `{ "access_token": "...", "token_type": "Bearer", "expires_in": 3600 }`.

### 2. Create a payout (requires the token + an Idempotency-Key)
```bash
curl -s -X POST http://localhost:8090/v1/payouts \
  -H "Authorization: Bearer <access_token>" \
  -H "Idempotency-Key: 9f1c2b7a-6e3d-4f21-aa10-77c0f2e4d8b1" \
  -H "Content-Type: application/json" \
  -d '{
        "amount": { "value": 1500.00, "currency": "USD" },
        "payout_currency": "PEN",
        "recipient": {
          "name": "Jose Arana",
          "bank": { "country": "PE", "account_number": "0011-0123-0100012345",
                    "bank_code": "BCP", "account_type": "checking" }
        },
        "reference": "Invoice #2026-10-17",
        "purpose": "services"
      }'
```
Returns `201` with `status: PROCESSING`, the applied `fx_rate`, and the `fee`.
**Re-run the same command (same Idempotency-Key)** → you get the *same* payout back, not a duplicate.

### 3. Read its status
```bash
curl -s http://localhost:8090/v1/payouts/<id> \
  -H "Authorization: Bearer <access_token>"
```
After ~5s it flips to `COMPLETED` — and the provider fires a **signed webhook** to the
configured callback (which, in this demo, is its own `/webhooks/payouts` receiver). Watch
the logs: you'll see `delivered payout.completed ...` then `VERIFIED webhook received ...`.

## What this teaches
- **Idempotency keys** stop double-payments on retry.
- **Async + webhooks** — the create call never returns a final result; you're notified later.
- **Signature verification** — a public webhook's only defense is the HMAC.
- **Status machine** — `PROCESSING → COMPLETED | FAILED | RETURNED`.

> Educational only. Not a real payment system and not production-ready.
