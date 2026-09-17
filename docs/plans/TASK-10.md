# Plan: TASK-10 — Concurrent load benchmark: where does p99 break 200 ms?

## Objective

The TASK-06 harness measures one request at a time, so the design target "p99 < 200 ms" is only proven for an idle system. This task adds a closed-loop concurrent harness that escalates concurrency per scenario until the 200 ms p99 target breaks, and records the break point in `benchmarks/results/`.

## Design decisions

- **Load model**: closed loop — a fixed pool of virtual-thread workers per level, each hammering requests and recording per-request latency. Levels: 1, 4, 16, 64, 128, 256 concurrent workers (256 deliberately exceeds Tomcat's default 200 threads and Hikari's default 10 connections, so saturation behavior is observed, not assumed).
- **Scenarios**: CREATE (payment write), RECORD_AUTHORIZATION (write + lifecycle, fresh pre-seeded payment per call), GET_DETAIL (read), LIST_PAYMENTS (paginated read from TASK-07), and MIXED (weighted ~50% read / 25% create / 15% authz / 10% list — near the design's 3:1 read ratio).
- **Measurement validity**: `http.maxConnections` raised before any HTTP (JDK's default 5 pooled connections would fake a bottleneck at the client). Per-level warmup (40 calls, discarded) absorbs JIT and pool warm-up. Total 240 measured calls per (scenario, level); each worker keeps a local latency list, merged into the shared nearest-rank `Stats`.
- **Break point**: first (scenario, level) whose p99 exceeds 200 ms; reported per scenario as the highest passing level. All levels still run so the curve shape is visible.
- **Reuse**: extract the nearest-rank `Stats` record from `LatencyBenchmark` into a package-private `Stats.java` so both harnesses share one quantile implementation.
- **New maven profile** `benchmark-load` (`./mvnw -q -Pbenchmark-load exec:java`) so the fast single-threaded baseline stays a separate, cheap run.
- **Reports**: `load-<date>.md` + `.json` with per-level tables and break points; results README updated with the finding.

## Steps

1. Extract `Stats` to `benchmarks/Stats.java`; update `LatencyBenchmark`.
2. `benchmarks/LoadBenchmark.java` — boot (same config as baseline), seed pools (200 readable payments, 100 fresh-for-authz payments), run scenarios × levels, write reports.
3. `pom.xml` — `benchmark-load` profile.
4. Run the harness; capture output; verify reports written.
5. Update `benchmarks/results/README.md` with the method and the measured break point.
6. Review scan, commit, record-progress.

## DoD

- Harness runs end-to-end and writes `load-<date>.{md,json}` with every scenario × level measured.
- The report states, per scenario, the highest concurrency whose p99 stayed under 200 ms and where it breaks.
- Single-threaded baseline profile still works; full test suite unaffected (35/35).
