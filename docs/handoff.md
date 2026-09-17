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

Remaining backlog: TASK-07 (pagination), TASK-08 (FEE transactions) — both open, no active task.

## How to run

```bash
cd backend  && ./mvnw spring-boot:run -Dspring-boot.run.arguments="--spring.profiles.active=dev"   # :8080
cd admin-portal && PAYMENT_API_KEY=sk_... npm run dev                                              # :3000
cd backend  && ./mvnw test                          # 26 tests
cd backend  && ./mvnw -q -Pbenchmark exec:java      # latency baseline -> benchmarks/results/
```

The portal needs `PAYMENT_API_KEY` (any ACTIVE merchant key from `GET /api/merchants`) for payment pages; merchant pages are open.

## Verified state

- 26/26 tests green: 6 repository, 16 MockMvc API slices, 3 RANDOM_PORT end-to-end, 1 context.
- Latency baseline 2026-09-17: p99 <= 23 ms on all four core ops (target 200 ms).
- Design system in `design.yaml`, intent gates PASS, slop scan clean.

## Non-obvious facts (also in memory.json)

- Boot 4 module splits hit this project three times: H2 console needs `spring-boot-h2console`, `TestRestTemplate` moved to `org.springframework.boot.resttestclient` (+ test-scope `spring-boot-restclient` for `RestTemplateBuilder`), Jackson is `tools.jackson` not `com.fasterxml`.
- Ledger rows persist only through `Payment`'s `CascadeType.ALL`; saving transactions separately double-inserts.
- `Payment.record()` rejects illegal transitions (409) and treats FAILED transactions as processor facts that always record.
- Admin trust model: merchant/actuator endpoints open, cross-merchant reads allowed on purpose; revisit triggers in TASK-04 plan and system-design.

## Suggested next session

Orient, then pick TASK-07 or TASK-08, or discuss the portal auth story (currently trusts the internal network).
