package com.fintech.ledger.PaymentGateway.benchmarks;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Minimal quantile container (nearest-rank on the sorted sample), shared by
 * the single-threaded baseline and the concurrent load harness.
 */
record Stats(double mean, double p50, double p95, double p99, double max) {

	static Stats of(List<Long> samples) {
		List<Long> sorted = new ArrayList<>(samples);
		Collections.sort(sorted);
		int n = sorted.size();
		double mean = sorted.stream().mapToLong(Long::longValue).average().orElse(0);
		double p50 = sorted.get((int) Math.ceil(0.50 * n) - 1);
		double p95 = sorted.get((int) Math.ceil(0.95 * n) - 1);
		double p99 = sorted.get((int) Math.ceil(0.99 * n) - 1);
		return new Stats(mean, p50, p95, p99, sorted.get(n - 1));
	}

	Map<String, Object> toMap() {
		return Map.of("mean_ms", mean, "p50_ms", p50, "p95_ms", p95, "p99_ms", p99, "max_ms", max);
	}

	String row(String operation) {
		return "| %s | %.1f | %.1f | %.1f | %.1f | %.1f |\n".formatted(operation, mean, p50, p95, p99, max);
	}
}
