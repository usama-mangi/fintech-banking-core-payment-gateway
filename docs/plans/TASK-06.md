# Plan: TASK-06 — API integration tests and benchmark baseline

## Objective

The API tests from TASK-02..04 cover behavior per endpoint but the full HTTP surface has never been exercised in one suite, and the capacity claims in `docs/system-design.md` are asserted, not measured. This task adds a cross-cutting integration suite (every endpoint, real HTTP over a random port) and records a measured latency baseline into `benchmarks/results/` so future performance work has a reference point.

## Dependencies

- TASK-01..05 closed. Existing 23 tests stay green.

## Design decisions

- Use `@SpringBootTest(webEnvironment = RANDOM_PORT)` + `TestRestTemplate` for one true HTTP round trip per endpoint (MockMvc skips the socket and servlet filter registration; this suite does not).
- Endpoints covered in one flow test: actuator health, merchant register, duplicate email 409, payment create 201/replay 200, ledger record authorize/capture/refund, detail, filters, 401/404/409 error shapes.
- A second test measures and *asserts bounds loosely* (e.g. p95 < 2000ms) purely to fail loudly on pathological regressions; real numbers are written to the benchmark file, not asserted hard.
- Benchmark: JMH is overkill for the MVP baseline (per system-design capacity section); use a repeatable in-JVM measurement harness run via a Maven-exec'd main class, writing JSON + markdown into `benchmarks/results/`. Document the method in the file so numbers are reproducible.
- Benchmark measures: POST /api/merchants, POST /api/payments (authed), GET /api/payments/{id}, POST transactions, each 200 iterations after 20 warmup, single-threaded, reporting mean/p50/p95/p99/max.

## Files to modify

| Action | Path | What |
|--------|------|------|
| CREATE | `backend/src/test/java/.../api/FullApiIntegrationTests.java` | RANDOM_PORT end-to-end suite across every endpoint |
| CREATE | `backend/src/main/java/.../benchmarks/LatencyBenchmark.java` | Standalone main(): boots the app, runs the measurement, writes `benchmarks/results/baseline-<date>.json` and `.md` |
| MODIFY | `backend/pom.xml` | exec-maven-plugin bound to a `benchmark` profile (not default build) |
| CREATE | `benchmarks/results/README.md` | How to run, method, what the baseline covers |
| CREATE | `benchmarks/results/baseline-2026-09-17.md` | The measured numbers |

## Implementation steps

1. `FullApiIntegrationTests`: full lifecycle across HTTP with TestRestTemplate; error-shape assertions for 400/401/404/409. Run `./mvnw test` — all 23 existing plus the new suite green.
2. `LatencyBenchmark` main: start app on a random port with dev-like config (H2), register a merchant, then measure the four operations; write JSON + markdown report. Verify manually via `./mvnw -q -Pbenchmark exec:java`.
3. Copy the produced report into `benchmarks/results/`; write `README.md` documenting method and rerun command.
4. Full gate: `./mvnw test`, `npm run build` untouched-but-green sanity on the portal is not needed here; commit results.

## Out of scope

- JMH harness, load testing beyond single-thread, CI wiring, portal benchmarks.

## Definition of done

- [ ] One integration suite covers every endpoint over real HTTP, including auth 401 and both 409 classes; suite green alongside all 23 existing tests
- [ ] `benchmarks/results/baseline-2026-09-17.md` exists with measured mean/p50/p95/p99 for the four core operations and the method documented
- [ ] `./mvnw test` exit 0

## Verification criteria

- [ ] Every DoD checkbox evidenced by test output and the committed benchmark file
- [ ] Tests · Build pass

## Rationale

A single end-to-end suite is the cheapest guard against wiring regressions (filter order, URL patterns, serialization) that per-endpoint slice tests can miss. The baseline is deliberately simple and reproducible rather than statistically perfect: the point is a reference number in the repo, not a certified benchmark.
