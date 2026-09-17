# Benchmark results

Latency baselines for the payment gateway API. Each `baseline-<date>.md` (with a JSON
twin) records one run of the single-threaded harness in
`backend/src/main/java/com/fintech/ledger/PaymentGateway/benchmarks/LatencyBenchmark.java`.
Each `load-<date>.md` (with a JSON twin) records one run of the concurrent closed-loop
harness in `benchmarks/LoadBenchmark.java`.

## Method

- App booted in-JVM against in-memory H2 on a random port (dev-like config, `ddl-auto=create-drop`).
- Single-threaded HTTP calls, no proxy, client and server share one JVM.
- 20 warmup calls per operation (discarded), then 200 measured calls.
- Quantiles are nearest-rank on the sorted sample.
- Each authorization gets a fresh payment because the ledger lifecycle forbids
  authorizing twice.

## Load method (load-*.md)

- Closed loop: a fixed worker pool per concurrency level (1, 4, 16, 64, 128, 256), each
  worker issuing requests continuously; per level 40 warmup then 240 measured calls.
- Scenarios: payment create, record-authorization (fresh pre-seeded payment per call),
  payment detail read, paginated list, and a 50/25/15/10 mixed workload.
- The shared HTTP client raises its keep-alive pool (`http.maxConnections=300`) so the
  client never becomes the bottleneck; per-worker latency shards avoid collection contention.
- AUTHORIZATION draws from a payment pool sized to the level; the ledger lifecycle
  forbids authorizing a payment twice.

## Rerun

```bash
cd backend
./mvnw -q -Pbenchmark exec:java        # single-threaded baseline -> baseline-<today>.*
./mvnw -q -Pbenchmark-load exec:java   # concurrent load sweep  -> load-<today>.*
```

The harnesses overwrite `<kind>-<today>.md` and `.json` in place. Compare against the
200 ms p99 target in `docs/system-design.md` before accepting a change that adds
per-request work.

## What the numbers say

Single-threaded baseline 2026-09-17 (Linux, Java 26, in-memory H2): every core operation
sits at p99 <= 23 ms, roughly 10x headroom under the 200 ms target. Treat sustained p99
above ~50 ms on this harness as a regression worth investigating, and above 200 ms as a
failure of the design target.

Concurrent load 2026-09-17 (same environment, closed loop):

- **Writes break the target first**, between 16 and 64 workers: payment create p99 97 ms
  at 16 -> 362 ms at 64; authorization 93 ms -> 481 ms. The pattern matches DB-connection
  queueing: default HikariCP allows 10 connections, so at 64+ in-flight writes most
  requests wait for a connection, not for CPU.
- **The paginated list breaks at 64 too** (232 ms) and stays over at 128/256 — a count
  query plus a page query doubles its connection hold time.
- **The detail read never breaks** through 256 workers (p99 149 ms), so the read path is
  not the constraint.
- **The mixed workload survives every level** (p99 195 ms at 256) because reads dominate
  it; the design's 3:1 read ratio holds up under load.

Bottom line: the 200 ms p99 target holds for realistic mixed traffic through at least
256 concurrent workers, but pure write bursts saturate around 16-64 workers on the
default connection pool. If write bursts matter in production, the first lever is
`spring.datasource.hikari.maximum-pool-size` (and confirming PostgreSQL behaves like
H2 here), not application code.

