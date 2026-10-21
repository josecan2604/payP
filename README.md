# payP — payout provider

A Spring Boot service that shows, end to end, how a real **payout
integration** is shaped — the same mechanics a provider like Payoneer/Stripe/Wise
exposes:

1. **Auth** — exchange client credentials for a short-lived bearer token.
2. **Payout API** — create a payout (**async** + **idempotent**), then read its status.
3. **Webhooks** — when the payout "settles", the provider POSTs a **signed** event to a
   callback URL; the receiver **verifies the HMAC signature** before trusting it.

No real money moves — the point is the mechanics.

## Architecture (3 services)
```
             ┌───────────────── payout-gateway (:8080) ─────────────────┐
client ────▶ │  validate JWT (via JWKS) · RBAC from roles · forward     │ ──▶ payout-resource (:8090)
             │  identity headers (X-Client-Id/X-Roles) · route          │      payout APIs + ledger
             └─────────────┬────────────────────────────────────────────┘      (ownership checks)
                           │ /oauth/**, /.well-known/**
                           ▼
                   payout-auth (:9000)
                   issues RS256 JWT + JWKS
```
- **payout-auth** — `client_credentials` → RS256 **JWT** with role claims; publishes **JWKS**.
- **payout-gateway** — the single public entry point. Validates the JWT (signature via the auth
  server's JWKS), enforces **RBAC** from the `roles` claim (e.g. only `ADMIN` may reconcile),
  then forwards the verified identity to the resource server as trusted headers.
- **payout-resource** — the payout APIs + double-entry ledger. Trusts the gateway's forwarded
  identity and does **per-object ownership** checks. Not exposed publicly.

> **Authz split:** the **gateway** does RBAC (role/route rules it can decide from the token);
> the **resource server** does resource-level ownership (is this payout yours?).

## Stack
- Java 21, Spring Boot 3.5, **Spring Cloud Gateway**, Spring Security OAuth2 (JWT/JWKS)
- **Postgres** via Spring Data JPA; schema owned by **Flyway** (`ddl-auto=validate`)
- **Double-entry ledger** — every money movement is a balanced transaction (debits == credits)
- HMAC-SHA256 webhook signatures
- RFC 9457 `ProblemDetail` error responses

## Double-entry ledger
Money is never created or lost — it only moves between accounts, and **every transaction
balances**. Chart of accounts:

| Account | Type | Meaning |
|---|---|---|
| `BANK_CASH` | ASSET | real money we hold at our bank |
| `CLIENT_FUNDS:<id>` | LIABILITY | a client's balance (what we owe them) |
| `PAYOUTS_IN_TRANSIT` | LIABILITY | committed payouts not yet settled |
| `FEE_INCOME` | INCOME | fees earned |

The lifecycle posts three balanced transactions:

```
FUNDING (on boot, per client)         DEBIT  BANK_CASH            100000
                                      CREDIT CLIENT_FUNDS:demo      100000

PAYOUT_COMMIT (on create)             DEBIT  CLIENT_FUNDS:demo      1515.00   (gross + fee)
                                      CREDIT PAYOUTS_IN_TRANSIT     1500.00   (gross)
                                      CREDIT FEE_INCOME               15.00   (fee)

PAYOUT_SETTLE (on completion)         DEBIT  PAYOUTS_IN_TRANSIT     1500.00
                                      CREDIT BANK_CASH              1500.00
```

Check a client's balance any time: `GET /v1/balance`. A payout that exceeds it is rejected
with `422` (`InsufficientFundsException`).

### Running-balance snapshot + reconciliation
Summing the whole journal on every read doesn't scale, so each posting also updates a cached
balance in the **same transaction** (`account_balance`). `GET /v1/balance` then reads a single
row instead of a `SUM` — and because the cache is written atomically with the entries, it gives
**read-your-writes** consistency. The journal stays the source of truth; the snapshot is a
rebuildable cache.

`GET /v1/reconcile` re-sums the journal per account and compares it to the snapshots — every
account must match (the check that proves the cache hasn't drifted):
```bash
curl -s http://localhost:8080/v1/reconcile -H "Authorization: Bearer <access_token>"
# -> [ { "account": "CLIENT_FUNDS:demo-client", "snapshot": 100000.00, "computed": 100000.00, "matches": true }, ... ]
```

A **nightly job** (`ReconciliationJob`, `@Scheduled` at `payout.reconciliation.cron`, default
03:00 UTC) runs the same check automatically and logs any mismatch at ERROR — the hook where a
real system would page on-call or rebuild the snapshot from the journal.

See the journal lines behind a payout: `GET /v1/payouts/{id}/entries` (scoped to the caller —
another client's payout returns `404`):
```bash
curl -s http://localhost:8080/v1/payouts/<id>/entries \
  -H "Authorization: Bearer <access_token>"
# -> commit lines (debit client funds, credit in-transit, credit fee),
#    then settlement lines (debit in-transit, credit bank cash)
```

Account statement — the caller's own ledger entries, newest first, paginated, optional
`type` filter: `GET /v1/ledger?page=0&size=20&type=PAYOUT_COMMIT` (size capped at 100):
```bash
curl -s "http://localhost:8080/v1/ledger?page=0&size=20" \
  -H "Authorization: Bearer <access_token>"
# -> { "content": [ ...entries on CLIENT_FUNDS:<you>, newest first... ],
#      "page": 0, "size": 20, "total_elements": 3, "total_pages": 1 }
```

Reverse/refund a payout: `POST /v1/payouts/{id}/reverse`. It posts a `PAYOUT_REVERSAL` that
mirrors every prior line, so funds return to the client and the status becomes `RETURNED`.
Works whether the payout is still `PROCESSING` (cancel) or already `COMPLETED` (a return/bounce).
A payout that can't be reversed (already returned / failed) gives `409`.
```bash
curl -s -X POST http://localhost:8080/v1/payouts/<id>/reverse \
  -H "Authorization: Bearer <access_token>"
```

## Run
Start Postgres, then each service (three terminals). All client traffic goes to the
**gateway on :8080** — the auth server and resource server aren't called directly.
```bash
# 0. build everything
mvn -q install -DskipTests

# 1. Postgres (schema created by Flyway on the resource server's first boot)
docker compose up -d

# 2. auth server (:9000)
mvn -pl payout-auth spring-boot:run

# 3. resource server (:8090)  — override DB via DB_URL / DB_USER / DB_PASSWORD
mvn -pl payout-resource spring-boot:run

# 4. gateway (:8080) — the only port you call
mvn -pl payout-gateway spring-boot:run
```

### Clients (configured in payout-auth)
| client_id | secret | roles |
|---|---|---|
| `demo-client` | `demo-secret` | `CLIENT` |
| `admin-client` | `admin-secret` | `ADMIN`, `CLIENT` |

`GET /v1/reconcile` requires **ADMIN** — `demo-client` gets `403`, `admin-client` succeeds.

### Schema (owned by Flyway — `payout-resource/src/main/resources/db/migration`)
- `payout` — one row per payout (amount, payout, fx_rate, fee, status, timestamps)
- `idempotency_key` — maps an Idempotency-Key to the payout it created, so retries don't duplicate.
  PK is composite `(client_id, idempotency_key)` so a key is **scoped per client** — one client can
  never retrieve another's payout by reusing a key (no cross-tenant/BOLA leak).
- `ledger_account` / `ledger_entry` — the double-entry ledger (append-only journal lines)

## Walk through the flow

### 1. Get a token
```bash
curl -s -X POST http://localhost:8080/oauth/token \
  -H "Content-Type: application/json" \
  -d '{"grant_type":"client_credentials","client_id":"demo-client","client_secret":"demo-secret"}'
```
Returns `{ "access_token": "...", "token_type": "Bearer", "expires_in": 3600 }`.

### 2. Create a payout (requires the token + an Idempotency-Key)
```bash
curl -s -X POST http://localhost:8080/v1/payouts \
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
curl -s http://localhost:8080/v1/payouts/<id> \
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

