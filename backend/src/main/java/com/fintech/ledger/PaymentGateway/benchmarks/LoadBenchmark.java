package com.fintech.ledger.PaymentGateway.benchmarks;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import com.fintech.ledger.PaymentGateway.PaymentGatewayApplication;
import tools.jackson.databind.ObjectMapper;

/**
 * Concurrent load harness. Boots the full app against in-memory H2 on a
 * random port, then runs a closed-loop load per scenario: a fixed pool of
 * workers per concurrency level, each issuing requests continuously and
 * recording client-observed latency.
 *
 * Method: levels 1, 4, 16, 64, 128, 256 concurrent workers; per level, 40
 * warmup calls (discarded) then 240 measured calls across the whole pool.
 * One shared client; the JDK keep-alive pool is raised via http.maxConnections
 * so the client never becomes the bottleneck. AUTHORIZATION calls draw from a
 * pre-seeded pool of fresh payments because the ledger lifecycle forbids
 * authorizing a payment twice.
 *
 * The report names, per scenario, the first concurrency level whose p99
 * exceeds the 200 ms design target — the measured break point.
 *
 * Run: ./mvnw -q -Pbenchmark-load exec:java  (from backend/)
 */
public class LoadBenchmark {

	private static final int[] CONCURRENCY_LEVELS = { 1, 4, 16, 64, 128, 256 };
	private static final int WARMUP_PER_LEVEL = 40;
	private static final int MEASURED_PER_LEVEL = 240;
	private static final int TARGET_P99_MS = 200;
	private static final int SEED_WORKERS = 8;

	private final RestTemplate http = new RestTemplate();
	private final ObjectMapper mapper = new ObjectMapper();
	private final String baseUri;
	private String apiKey;

	public LoadBenchmark(String baseUri) {
		this.baseUri = baseUri;
	}

	public static void main(String[] args) throws Exception {
		// Raise the JDK per-host keep-alive pool above the highest worker
		// count so persistent connections are retained, not churned.
		System.setProperty("http.maxConnections", "300");
		SpringApplication app = new SpringApplication(PaymentGatewayApplication.class);
		app.setDefaultProperties(Map.of(
				"server.port", "0",
				"spring.datasource.url", "jdbc:h2:mem:load-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1",
				"spring.datasource.username", "sa",
				"spring.datasource.password", "",
				"spring.jpa.hibernate.ddl-auto", "create-drop",
				"spring.jpa.open-in-view", "false"));
		long start = System.nanoTime();
		try (ConfigurableApplicationContext context = app.run(args)) {
			String baseUri = "http://localhost:" + context.getEnvironment().getProperty("local.server.port");
			long bootMs = (System.nanoTime() - start) / 1_000_000;
			new LoadBenchmark(baseUri).run(bootMs);
		}
		System.exit(0);
	}

	private enum Scenario {
		CREATE("POST /api/payments"),
		RECORD_AUTHORIZATION("POST /api/payments/{id}/transactions (AUTHORIZATION)"),
		GET_DETAIL("GET /api/payments/{id}"),
		LIST_PAYMENTS("GET /api/payments?page=0&size=20"),
		MIXED("MIXED 50% read / 25% create / 15% authz / 10% list");

		private final String label;

		Scenario(String label) {
			this.label = label;
		}
	}

	private void run(long bootMs) throws IOException, InterruptedException {
		System.out.println("Seeding payment pools...");
		this.apiKey = registerMerchant();
		List<String> readablePayments = seedPayments(200, "load-read");
		System.out.println("Seeded " + readablePayments.size() + " readable payments.");

		Map<String, LinkedHashMap<Integer, Stats>> curves = new LinkedHashMap<>();
		Map<String, Integer> firstBreak = new LinkedHashMap<>();

		for (Scenario scenario : Scenario.values()) {
			LinkedHashMap<Integer, Stats> curve = new LinkedHashMap<>();
			int breakLevel = -1;
			for (int level : CONCURRENCY_LEVELS) {
				Stats stats = runLevel(scenario, level, readablePayments);
				curve.put(level, stats);
				boolean broke = stats.p99() > TARGET_P99_MS;
				if (broke && breakLevel == -1) {
					breakLevel = level;
				}
				System.out.printf("%-46s level=%3d p99=%7.1f ms%s%n",
						scenario.label, level, stats.p99(), broke ? "  << breaks target" : "");
			}
			curves.put(scenario.label, curve);
			firstBreak.put(scenario.label, breakLevel);
		}

		writeReports(curves, firstBreak, bootMs);
	}

	private Stats runLevel(Scenario scenario, int level, List<String> readablePayments)
			throws InterruptedException {
		final List<String> authzPool;
		if (scenario == Scenario.RECORD_AUTHORIZATION || scenario == Scenario.MIXED) {
			int expectedAuthz = (int) Math.ceil(
					(scenario == Scenario.MIXED ? 0.15 : 1.0) * (WARMUP_PER_LEVEL + MEASURED_PER_LEVEL)) + 20;
			authzPool = seedPayments(expectedAuthz, "load-authz-l" + level);
		}
		else {
			authzPool = List.of();
		}

		AtomicInteger readCursor = new AtomicInteger();
		AtomicInteger authzCursor = new AtomicInteger();
		// Warmup pass, discarded: absorbs JIT and pool warm-up at this level.
		executePass(level, WARMUP_PER_LEVEL,
				i -> runCall(scenario, readablePayments, authzPool, readCursor, authzCursor));
		List<Long> latencies = executePass(level, MEASURED_PER_LEVEL,
				i -> runCall(scenario, readablePayments, authzPool, readCursor, authzCursor));
		return Stats.of(latencies);
	}

	private void runCall(Scenario scenario, List<String> readablePayments, List<String> authzPool,
			AtomicInteger readCursor, AtomicInteger authzCursor) {
		switch (scenario) {
			case CREATE -> createPayment("load-" + UUID.randomUUID().toString().substring(0, 12));
			case RECORD_AUTHORIZATION -> recordAuthorization(nextAuthz(authzPool, authzCursor));
			case GET_DETAIL -> get("/api/payments/" + nextRead(readablePayments, readCursor));
			case LIST_PAYMENTS -> get("/api/payments?page=0&size=20");
			case MIXED -> {
				double roll = ThreadLocalRandom.current().nextDouble();
				if (roll < 0.50) {
					get("/api/payments/" + nextRead(readablePayments, readCursor));
				}
				else if (roll < 0.75) {
					createPayment("load-mix-" + UUID.randomUUID().toString().substring(0, 12));
				}
				else if (roll < 0.90) {
					recordAuthorization(nextAuthz(authzPool, authzCursor));
				}
				else {
					get("/api/payments?page=0&size=20");
				}
			}
		}
	}

	/**
	 * Runs {@code totalCalls} through a fixed pool of {@code level} workers
	 * and returns the per-call latencies in milliseconds. Each worker records
	 * into its own shard, so collection adds no contention.
	 */
	private List<Long> executePass(int level, int totalCalls, Call call) throws InterruptedException {
		ExecutorService pool = Executors.newFixedThreadPool(level);
		try {
			List<List<Long>> shards = new ArrayList<>(level);
			for (int w = 0; w < level; w++) {
				shards.add(new ArrayList<>());
			}
			AtomicInteger cursor = new AtomicInteger();
			for (int w = 0; w < level; w++) {
				final int worker = w;
				pool.submit(() -> {
					List<Long> shard = shards.get(worker);
					for (;;) {
						int i = cursor.getAndIncrement();
						if (i >= totalCalls) {
							break;
						}
						long started = System.nanoTime();
						call.run(i);
						shard.add((System.nanoTime() - started) / 1_000_000);
					}
				});
			}
			pool.shutdown();
			if (!pool.awaitTermination(10, TimeUnit.MINUTES)) {
				throw new IllegalStateException("load pass did not finish in time (level " + level + ")");
			}
			return shards.stream().flatMap(List::stream).toList();
		}
		finally {
			pool.shutdownNow();
		}
	}

	private interface Call {
		void run(int i);
	}

	private String nextRead(List<String> readablePayments, AtomicInteger cursor) {
		return readablePayments.get(cursor.getAndIncrement() % readablePayments.size());
	}

	private String nextAuthz(List<String> authzPool, AtomicInteger cursor) {
		int index = cursor.getAndIncrement();
		if (index >= authzPool.size()) {
			throw new IllegalStateException("authz payment pool exhausted; seed count too low");
		}
		return authzPool.get(index);
	}

	/** Creates {@code count} payments concurrently and returns their ids. */
	private List<String> seedPayments(int count, String prefix) throws InterruptedException {
		ExecutorService pool = Executors.newFixedThreadPool(SEED_WORKERS);
		try {
			List<String> ids = Collections.synchronizedList(new ArrayList<>(count));
			AtomicInteger cursor = new AtomicInteger();
			for (int w = 0; w < SEED_WORKERS; w++) {
				pool.submit(() -> {
					for (;;) {
						int i = cursor.getAndIncrement();
						if (i >= count) {
							break;
						}
						ids.add(createPayment(prefix + "-" + i));
					}
				});
			}
			pool.shutdown();
			if (!pool.awaitTermination(10, TimeUnit.MINUTES)) {
				throw new IllegalStateException("seeding did not finish in time");
			}
			return new ArrayList<>(ids);
		}
		finally {
			pool.shutdownNow();
		}
	}

	private String registerMerchant() {
		String email = "load-owner-" + UUID.randomUUID().toString().substring(0, 8) + "@ledger.test";
		String body = post("/api/merchants", json(Map.of("businessName", "Load Owner", "email", email)), null);
		return mapper.readTree(body).get("apiKey").asText();
	}

	private String createPayment(String idempotencyKey) {
		String body = post("/api/payments",
				json(Map.of("amount", "10.00", "currency", "USD", "description", "load payment",
						"idempotencyKey", idempotencyKey)),
				apiKey);
		return mapper.readTree(body).get("id").asText();
	}

	private void recordAuthorization(String paymentId) {
		post("/api/payments/" + paymentId + "/transactions",
				json(Map.of("type", "AUTHORIZATION", "amount", "10.00")), apiKey);
	}

	private String post(String path, String json, String apiKey) {
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		if (apiKey != null) {
			headers.set("X-API-Key", apiKey);
		}
		ResponseEntity<String> response = http.postForEntity(baseUri + path, new HttpEntity<>(json, headers), String.class);
		if (!response.getStatusCode().is2xxSuccessful()) {
			throw new IllegalStateException("load call failed: " + path + " -> " + response.getStatusCode());
		}
		return response.getBody();
	}

	private void get(String path) {
		HttpHeaders headers = new HttpHeaders();
		headers.set("X-API-Key", apiKey);
		ResponseEntity<String> response = http.exchange(baseUri + path, HttpMethod.GET, new HttpEntity<>(headers), String.class);
		if (!response.getStatusCode().is2xxSuccessful()) {
			throw new IllegalStateException("load call failed: " + path + " -> " + response.getStatusCode());
		}
	}

	private static String json(Map<String, String> body) {
		StringBuilder sb = new StringBuilder("{");
		boolean first = true;
		for (Map.Entry<String, String> entry : body.entrySet()) {
			if (!first) {
				sb.append(",");
			}
			sb.append("\"").append(entry.getKey()).append("\":\"").append(entry.getValue()).append("\"");
			first = false;
		}
		return sb.append("}").toString();
	}

	private void writeReports(Map<String, LinkedHashMap<Integer, Stats>> curves,
			Map<String, Integer> firstBreak, long bootMs) throws IOException {
		Path resultsDir = Path.of("..", "benchmarks", "results");
		Files.createDirectories(resultsDir);
		String date = Instant.now().toString().substring(0, 10);

		Map<String, Object> json = new LinkedHashMap<>();
		json.put("date", date);
		json.put("method", "closed-loop, " + WARMUP_PER_LEVEL + " warmup + " + MEASURED_PER_LEVEL
				+ " measured per (scenario, level), in-memory H2, JVM-local HTTP, shared client with raised keep-alive pool");
		json.put("target_p99_ms", TARGET_P99_MS);
		json.put("boot_ms", bootMs);
		json.put("first_break_level", firstBreak);
		curves.forEach((scenario, byLevel) -> {
			Map<String, Object> levelStats = new LinkedHashMap<>();
			byLevel.forEach((level, stats) -> levelStats.put(String.valueOf(level), stats.toMap()));
			json.put(scenario, levelStats);
		});
		Files.writeString(resultsDir.resolve("load-" + date + ".json"),
				mapper.writerWithDefaultPrettyPrinter().writeValueAsString(json), StandardCharsets.UTF_8);

		StringBuilder md = new StringBuilder();
		md.append("# Concurrent load benchmark — ").append(date).append("\n\n");
		md.append("Closed loop: a fixed worker pool per level drives the app on in-memory H2 (JVM-local).\n");
		md.append("Per level: ").append(WARMUP_PER_LEVEL).append(" warmup, ").append(MEASURED_PER_LEVEL)
				.append(" measured calls. Design target: p99 < ").append(TARGET_P99_MS).append(" ms.\n\n");
		curves.forEach((scenario, byLevel) -> {
			md.append("## ").append(scenario).append("\n\n");
			md.append("| workers | mean | p50 | p95 | p99 | max |\n");
			md.append("|---------|------|-----|-----|-----|-----|\n");
			byLevel.forEach((level, stats) -> md.append(
					"| %d | %.1f | %.1f | %.1f | %.1f | %.1f |\n".formatted(level, stats.mean(), stats.p50(), stats.p95(), stats.p99(), stats.max())));
			int breakLevel = firstBreak.get(scenario);
			md.append("\nFirst level over target: ")
					.append(breakLevel == -1 ? "none through " + CONCURRENCY_LEVELS[CONCURRENCY_LEVELS.length - 1] : breakLevel)
					.append(".\n\n");
		});
		md.append("Boot time ").append(bootMs).append(" ms. Environment: ")
				.append(System.getProperty("os.name")).append(", Java ").append(System.getProperty("java.version")).append(".\n");
		md.append("Rerun: `./mvnw -q -Pbenchmark-load exec:java` from `backend/`.\n");
		Files.writeString(resultsDir.resolve("load-" + date + ".md"), md.toString(), StandardCharsets.UTF_8);
		System.out.println("Reports written to " + resultsDir.toAbsolutePath().normalize());
	}
}
