# System design — fintech-banking-core-payment-gateway

## 1. Requirements

### Functional
- Actors: merchants (submit and manage payments via API), admin staff (review merchants and trace payments in the admin portal).
- Critical flows:
  1. Register a merchant, issue API key.
  2. Merchant submits a payment (amount, currency, description, idempotency key).
  3. Record authorization → capture on a payment; payment status follows the ledger.
  4. Refund a captured payment (full or partial amounts recorded as transactions).
  5. Admin traces a payment: full chronological transaction list with statuses.
- Inputs: JSON over REST. Outputs: JSON payment/merchant/transaction resources with status codes.
- Operations: create/read on merchants; create/read + ledger-record on payments; list/search by merchant and status.

### Non-functional

| Dimension | Target |
|-----------|--------|
| Scale | Single-node MVP: tens of merchants, low QPS (< 50 peak). No horizontal scaling yet. |
| Latency | p99 < 200 ms for API calls on local network. |
| Availability | Dev-grade: restart-safe, no multi-AZ story. |
| Consistency | Strong: single PostgreSQL/H2 instance, ACID per request. |
| Durability | Postgres in prod; H2 in-memory acceptable in dev because data is disposable. |
| Data volume | Small (< 1M payments/year at MVP scale). |
| Read/write ratio | ~3:1 (portals read more than they write). |

### Constraints
- Java 21, Spring Boot 4.1.1 (parent BOM already pinned), Maven wrapper.
- Next.js 15 / React 19 / Tailwind 4 already scaffolded for the portal.
- Solo developer; favor boring, mainstream Spring patterns over exotic ones.
- Money correctness is a hard regulatory-adjacent requirement: exact decimal arithmetic.

### Security
- Data classification: merchant PII (business name, email), API keys, payment amounts. No cardholder data (PAN/CVV never stored; this gateway records ledger entries, not card details).
- Access model: static per-merchant API keys (`X-API-Key` header) for the payment API; admin portal trusted on internal network for MVP.
- Encryption: TLS terminated outside app for MVP; secrets via environment variables, never committed.
- Auditability: `BaseEntity` audit columns plus append-only transaction ledger; no updates or deletes on transactions.

### Intent
Merchants and admins answer "what is the state and history of this payment" with one query, with amounts that are always exact.

## 2. Capacity estimation

- QPS: 50 merchants × 200 actions/day ÷ 86,400 ≈ 0.1 avg, ~1 peak. Trivial; JPA + H2/Postgres handles this without tuning.
- Storage: 5,000 payments/day × ~1 KB (payment + 3 transactions) × 365 ≈ 1.8 GB/year. Fits a single Postgres node comfortably.
- Bandwidth: negligible (< 1 Mbps).
- Latency budget: 200 ms p99 ÷ ~20 ms per JPA round trip ≈ 10 hops available. A controller + service + repository chain uses 3.

## 3. Component decomposition

```
admin-portal (Next.js)  ──HTTP/JSON──▶  backend REST API (/api)  ──▶  service layer
merchant clients ───────────────────────────────────────────────────────────┘
                                                                        │
                                                              Spring Data JPA
                                                                        │
                                                              H2 (dev) / PostgreSQL (prod)
```

- REST controllers: request validation (Bean Validation), DTO mapping, status codes.
- Service layer: use-case orchestration, transaction boundaries (`@Transactional`), idempotency handling.
- Domain: entities own invariants and status transitions (`Payment.record()`).
- Repositories: Spring Data JPA, derived queries only for MVP.
- Single point of failure: the database. Acceptable at MVP scale; document backup policy for Postgres.

## 4. Data flow & API contract

REST, JSON, synchronous. Draft contracts (to be finalized in implementation tasks):

```
POST   /api/merchants                                   → 201 MerchantResponse
GET    /api/merchants?status=&page=0&size=20            → 200 Page<MerchantResponse>
GET    /api/merchants/{id}                              → 200 MerchantResponse | 404
GET    /api/payments?merchantId=&status=&page=&size=    → 200 Page<PaymentSummary>
POST   /api/payments                                    → 201 PaymentResponse (idempotent by idempotencyKey)
GET    /api/payments/{id}                               → 200 PaymentResponse (includes transactions) | 404
POST   /api/payments/{id}/transactions                  → 201 TransactionResponse (validates transition)
GET    /actuator/health                                 → liveness for ops
```

List endpoints return a page envelope: `{ "content": [...], "page", "size", "totalElements", "totalPages" }` — `size` clamped to 1..100, sort fixed server-side to `createdAt` descending. Element shapes are unchanged.

- Errors: `{ "status", "error", "message", "timestamp" }` via a `@RestControllerAdvice`; 400 validation, 404 unknown id, 409 illegal state transition, 422 domain rule violation.
- Idempotency: `payments.idempotency_key` unique; duplicate POST returns the original payment (200, not 201).
- Versioning: none yet; `/api` prefix reserves room for `/api/v2`.

## 5. Trade-offs

**Option A — Monolithic Spring Boot module (chosen).** Controllers, services, entities in one deployable. Pros: zero operational overhead, one transaction boundary, fastest path to a working ledger. Cons: no independent scaling, package boundaries enforced only by convention. Choose it because MVP traffic (≈1 QPS peak) never justifies more.

**Option B — Separate ledger service + payment service.** Pros: independent deploys, clear ownership. Cons: distributed transactions for a use case that fits one ACID commit; two codebases to maintain solo. Rejected for now.

**Axis resolutions:**
- Consistency over availability: single DB, ACID per request; payments must not double-capture. CAP tension is theoretical at this scale.
- Simplicity over flexibility: static API keys instead of OAuth/JWT until a concrete driver (external partners) exists.
- Write normalization over read denormalization: `capturedAmountInUsd()` computes on read; recompute if read latency ever matters (it will not at this scale).
- Push status transitions into the domain entity (`Payment.record()`) rather than the service: illegal transitions are impossible wherever a payment is touched. Cost: services must load entities rather than issue bulk updates. Acceptable at ~1 QPS.

## 6. Validation & measurement

- Assumption "H2 dev database behaves like Postgres for our schema" → repository tests run against H2 now; a smoke run against Postgres via Testcontainers is the revisit trigger if schema divergence appears.
- Assumption "static API keys are enough" → revisit when a second consumer class appears or keys need rotation.
- DoD for every API task: integration test hits the endpoint, asserts status code and body, and fails on the unmodified codebase.
- Benchmark task writes results into `benchmarks/results/` so capacity claims stay measured, not asserted.

## 7. Decision

Monolithic Spring Boot 4 API + Next.js admin portal, H2 dev / PostgreSQL prod, static API-key auth, domain-owned status transitions, append-only ledger. Revisit triggers: > 50 QPS sustained, multi-team ownership, external partner access requiring OAuth, or Postgres schema divergence from H2 tests.
