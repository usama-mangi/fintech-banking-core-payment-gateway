# Benchmark results

Latency baselines for the payment gateway API. Each `baseline-<date>.md` (with a JSON
twin) records one run of the harness in
`backend/src/main/java/com/fintech/ledger/PaymentGateway/benchmarks/LatencyBenchmark.java`.

## Method

- App booted in-JVM against in-memory H2 on a random port (dev-like config, `ddl-auto=create-drop`).
- Single-threaded HTTP calls, no proxy, client and server share one JVM.
- 20 warmup calls per operation (discarded), then 200 measured calls.
- Quantiles are nearest-rank on the sorted sample.
- Each authorization gets a fresh payment because the ledger lifecycle forbids
  authorizing twice.

## Rerun

```bash
cd backend
./mvnw -q -Pbenchmark exec:java
```

The harness overwrites `baseline-<today>.md` and `.json` in place. Compare against the
200 ms p99 target in `docs/system-design.md` before accepting a change that adds
per-request work.

## What the numbers say

Baseline 2026-09-17 (Linux, Java 26, in-memory H2): every core operation sits at
p99 <= 23 ms, roughly 10x headroom under the 200 ms target. Treat sustained p99 above
~50 ms on this harness as a regression worth investigating, and above 200 ms as a
failure of the design target.
