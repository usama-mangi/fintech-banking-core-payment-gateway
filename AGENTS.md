# AGENTS.md - fintech-banking-core-payment-gateway

## What

A payment gateway platform for merchants. The backend (Spring Boot 4, Java 21) keeps a ledger of payments and transactions: merchants register, submit a payment, then record authorization, capture, refund, chargeback and fee entries against it. Payment state moves through REQUIRES_PAYMENT → AUTHORIZED → CAPTURED → REFUNDED or CANCELLED, with FAILED and DECLINED as terminal failure states. The admin portal (Next.js 15, React 19, Tailwind 4) is the internal operations surface where staff review merchants and trace the full transaction history of any payment.

## Why

Merchant payment processing usually spreads across a bank portal, spreadsheets and a hand-rolled reconciliation script. Each holds a partial copy of the truth, so answering "was this payment captured and what fees were charged" means checking three places. This project keeps one append-only transaction ledger per payment in one database, so status questions get a single query with no reconciliation step.

## Standard

Production grade. Every behavior the task names gets a test, and the build plus all tests pass before a task closes. Amounts are `BigDecimal` with scale 2 and currency handling uses explicit rates, never float. API boundaries validate input (Bean Validation at controllers) and the domain rejects invalid state transitions. Nothing ships on a green-untested claim.

## Non-Negotiables

- Money is `BigDecimal` (14,2) end to end; no double or float anywhere near amounts.
- Domain invariants in entities: constructors and setters reject null, blank and non-positive amounts. Controllers validate DTOs with Bean Validation.
- Test behavior, not structure: each task's DoD includes tests that fail without the new code.
- H2 in-memory for dev and tests, PostgreSQL driver on the classpath for production.
- Plans live in `docs/plans/TASK-XX.md` before multi-file work starts.

## Architecture

| Component | Role |
|-----------|------|
| `backend/` | Spring Boot 4.1.1, Java 21, Maven (`./mvnw`). REST API under `/api`. spring-boot-starter-webmvc, data-jpa, validation, actuator. H2 in dev/tests, PostgreSQL in prod. |
| `admin-portal/` | Next.js 15.5 (App Router), React 19, Tailwind 4. Talks to the backend REST API. |
| `benchmarks/results/` | Benchmark output (empty; to be used for performance tasks). |

Domain model: `Merchant` 1—N `Payment` 1—N `Transaction`. `BaseEntity` carries auditing fields. `Payment.record()` owns status transitions and keeps the ledger chronological. Repositories are Spring Data JPA interfaces. Packages: `domain`, `repository`, plus `api`, `service`, `config` as they appear. API-key auth via `X-API-Key` headers (static keys per merchant) is the first auth model.

Data flow: admin portal or merchant client → REST API → service layer → JPA repository → H2/PostgreSQL. Amount conversions use `Currency.getUsdRate()` for indicative USD totals.
