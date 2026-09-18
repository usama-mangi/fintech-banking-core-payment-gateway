# Handoff — 2026-09-17

## What this project is

Payment gateway ledger: Spring Boot 4.1.1 / Java 21 backend (Maven, `backend/`) + Next.js 15.5 admin portal (`admin-portal/`). One append-only transaction ledger per payment; `Payment.record()` owns all status transitions. Read `AGENTS.md` first, then `docs/system-design.md` for the architecture decision and its revisit triggers.

## Where the work stands (all committed on `main`)

| Task | State | Commit |
|------|-------|--------|
| TASK-01 profiles: H2 dev console, env-var prod, pinned test profile | closed | `6c910f6` |
| TASK-02 merchants API + error contract `{status,error,message,timestamp}` | closed | `54462e5` |
| TASK-03 payments + ledger API, idempotent create (201/200), 409 transitions | closed | `3c77a39` |
| TASK-04 X-API-Key auth filter on `/api/payments/*` | closed | `6bef16f` |
| TASK-05 admin portal: merchants, payments list, detail ledger views | closed | `e738889` |
| TASK-06 end-to-end HTTP suite + latency baseline | closed | `abc15d8` |
| TASK-07 pagination envelope on list endpoints | closed | `2e47410` |
| TASK-08 FEE transactions in the ledger | closed | `a7d7233` |
| TASK-09 portal passphrase auth + signed sessions | closed | `100ad13` |
| TASK-10 concurrent load harness + 200ms break point | closed | `10d21a5` |
| TASK-11 merchant lifecycle endpoints + portal actions | closed | `8481b4c` |
| TASK-12 portal pagination controls from envelope totals | closed | `585e926` |
| TASK-13 fee aggregation endpoint `/api/fees` | closed | this commit |
| TASK-14 scoped auth: internal key tier + ownership scoping | closed | this commit |
| TASK-15 CSV export `/api/payments/export` | closed | this commit |
| TASK-16 merchant dashboard (Thymeleaf) + portal `/fees` view | closed | this commit |
| TASK-17 merchant portal (standalone Next.js, API-key sign-in) | closed | `a35f100` + `07dc287` |

Backlog is empty; next tasks are proposals, not commitments.

## How to run

```bash
cd backend  && ./mvnw spring-boot:run -Dspring-boot.run.arguments="--spring.profiles.active=dev"   # :8080
cd admin-portal && PAYMENT_API_KEY=sk_... PORTAL_PASSCODE=... npm run dev                          # :3000
cd merchant-portal && MERCHANT_SESSION_SECRET=... npm run dev                                      # :3200
cd backend  && ./mvnw test                          # 56 tests
cd backend  && ./mvnw -q -Pbenchmark exec:java      # latency baseline -> benchmarks/results/
```

The portal needs `PAYMENT_API_KEY` (any ACTIVE merchant key from `GET /api/merchants`) for its gateway calls, and `PORTAL_PASSCODE` for staff sign-in — without it, every staff view redirects to `/login` and login fails closed.

## Verified state

- 56/56 tests green: 6 repository, API slices (incl. fee report, scoping, CSV export, pagination, dashboard), lifecycle domain tests, end-to-end, 1 context.
- Latency baseline 2026-09-17: p99 <= 23 ms on all four core ops (target 200 ms).
- Design system in `design.yaml`, intent gates PASS, slop scan clean.

## Non-obvious facts (also in memory.json)

- Boot 4 module splits hit this project three times: H2 console needs `spring-boot-h2console`, `TestRestTemplate` moved to `org.springframework.boot.resttestclient` (+ test-scope `spring-boot-restclient` for `RestTemplateBuilder`), Jackson is `tools.jackson` not `com.fasterxml`.
- Ledger rows persist only through `Payment`'s `CascadeType.ALL`; saving transactions separately double-inserts.
- `Payment.record()` rejects illegal transitions (409) and treats FAILED transactions as processor facts that always record.
- Admin trust model: merchant/actuator endpoints open, cross-merchant reads allowed on purpose; the portal itself is gated by a shared passphrase (`PORTAL_PASSCODE`) with an HMAC-signed 12-hour cookie (`ledger_session`), stateless — rotating the passphrase invalidates all sessions. Revisit triggers in system-design (per-user accounts when a second admin consumer appears).

## Suggested next session

Orient, then consider: a lifecycle audit trail (who suspended which merchant, when), login rate limiting for the portal passphrase, or re-testing the load break point with a larger Hikari pool (TASK-10 memory has the harness details).
