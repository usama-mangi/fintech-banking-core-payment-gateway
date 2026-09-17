package com.fintech.ledger.PaymentGateway.benchmarks;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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
 * Latency baseline harness. Boots the full app against in-memory H2 on a
 * random port, then measures single-threaded request latency for the four
 * core operations: merchant registration, payment creation, transaction
 * recording and payment detail reads.
 *
 * Method: 20 warmup calls per operation (discarded), then 200 measured
 * calls. Reports mean, p50, p95, p99 and max in milliseconds. Results are
 * written to benchmarks/results/ as JSON and markdown.
 *
 * Run: ./mvnw -q -Pbenchmark exec:java  (from backend/)
 */
public class LatencyBenchmark {

	private static final int WARMUP = 20;
	private static final int ITERATIONS = 200;

	private final RestTemplate http = new RestTemplate();
	private final ObjectMapper mapper = new ObjectMapper();
	private final String baseUri;
	private String apiKey;

	public LatencyBenchmark(String baseUri, String apiKey) {
		this.baseUri = baseUri;
		this.apiKey = apiKey;
	}

	public static void main(String[] args) throws Exception {
		SpringApplication app = new SpringApplication(PaymentGatewayApplication.class);
		app.setDefaultProperties(Map.of(
				"server.port", "0",
				"spring.datasource.url", "jdbc:h2:mem:benchmark-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1",
				"spring.datasource.username", "sa",
				"spring.datasource.password", "",
				"spring.jpa.hibernate.ddl-auto", "create-drop",
				"spring.jpa.open-in-view", "false"));
		long start = System.nanoTime();
		try (ConfigurableApplicationContext context = app.run(args)) {
			String baseUri = "http://localhost:" + context.getEnvironment().getProperty("local.server.port");
			long bootMs = (System.nanoTime() - start) / 1_000_000;

			LatencyBenchmark benchmark = new LatencyBenchmark(baseUri, null);
			String apiKey = benchmark.registerMerchant();
			benchmark.apiKey = apiKey;
			Map<String, Stats> results = benchmark.runAll();
			benchmark.writeReports(results, bootMs);
		}
		System.exit(0);
	}

	private Map<String, Stats> runAll() {
		Map<String, Stats> results = new LinkedHashMap<>();
		String idempotencyBase = "bench-" + UUID.randomUUID().toString().substring(0, 8);

		results.put("POST /api/merchants", measure(() -> {
			String email = "bench-" + UUID.randomUUID().toString().substring(0, 12) + "@ledger.test";
			post("/api/merchants", json(Map.of("businessName", "Bench Co", "email", email)), null);
			return "";
		}));

		String firstPaymentId = "";
		List<Long> createLatencies = new ArrayList<>();
		for (int i = 0; i < WARMUP; i++) {
			createPayment(idempotencyBase + "-w" + i);
		}
		for (int i = 0; i < ITERATIONS; i++) {
			long started = System.nanoTime();
			String id = createPayment(idempotencyBase + "-" + i);
			createLatencies.add((System.nanoTime() - started) / 1_000_000);
			if (firstPaymentId.isEmpty()) {
				firstPaymentId = id;
			}
		}
		results.put("POST /api/payments", Stats.of(createLatencies));

		List<Long> txLatencies = new ArrayList<>();
		// Each authorization moves the payment to AUTHORIZED, so every call
		// (warmup and measured) needs a fresh payment.
		for (int i = 0; i < WARMUP; i++) {
			recordTransaction(createPayment(idempotencyBase + "-txw" + i), "AUTHORIZATION", "1.00");
		}
		// The lifecycle forces sequential states; measure on separate payments.
		List<String> txPaymentIds = new ArrayList<>();
		for (int i = 0; i < ITERATIONS; i++) {
			txPaymentIds.add(createPayment(idempotencyBase + "-tx" + i));
		}
		for (String paymentId : txPaymentIds) {
			long started = System.nanoTime();
			recordTransaction(paymentId, "AUTHORIZATION", "1.00");
			txLatencies.add((System.nanoTime() - started) / 1_000_000);
		}		results.put("POST /api/payments/{id}/transactions", Stats.of(txLatencies));

		final String detailPaymentId = firstPaymentId;
		results.put("GET /api/payments/{id}", measure(() -> {
			get("/api/payments/" + detailPaymentId);
			return "";
		}));

		return results;
	}

	private interface Call {
		String run();
	}

	private Stats measure(Call call) {
		List<Long> latencies = new ArrayList<>();
		for (int i = 0; i < WARMUP; i++) {
			call.run();
		}
		for (int i = 0; i < ITERATIONS; i++) {
			long started = System.nanoTime();
			call.run();
			latencies.add((System.nanoTime() - started) / 1_000_000);
		}
		return Stats.of(latencies);
	}

	private String registerMerchant() {
		String email = "bench-owner-" + UUID.randomUUID().toString().substring(0, 8) + "@ledger.test";
		String body = post("/api/merchants", json(Map.of("businessName", "Bench Owner", "email", email)), null);
		return mapper.readTree(body).get("apiKey").asText();
	}

	private String createPayment(String idempotencyKey) {
		String body = post("/api/payments",
				json(Map.of("amount", "10.00", "currency", "USD", "description", "benchmark payment",
						"idempotencyKey", idempotencyKey)),
				apiKey);
		return mapper.readTree(body).get("id").asText();
	}

	private void recordTransaction(String paymentId, String type, String amount) {
		post("/api/payments/" + paymentId + "/transactions", json(Map.of("type", type, "amount", amount)), apiKey);
	}

	private String post(String path, String json, String apiKey) {
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		if (apiKey != null) {
			headers.set("X-API-Key", apiKey);
		}
		ResponseEntity<String> response = http.postForEntity(baseUri + path, new HttpEntity<>(json, headers), String.class);
		if (!response.getStatusCode().is2xxSuccessful()) {
			throw new IllegalStateException("benchmark call failed: " + path + " -> " + response.getStatusCode());
		}
		return response.getBody();
	}

	private void get(String path) {
		HttpHeaders headers = new HttpHeaders();
		headers.set("X-API-Key", apiKey);
		ResponseEntity<String> response = http.exchange(baseUri + path, HttpMethod.GET, new HttpEntity<>(headers), String.class);
		if (!response.getStatusCode().is2xxSuccessful()) {
			throw new IllegalStateException("benchmark call failed: " + path + " -> " + response.getStatusCode());
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

	private void writeReports(Map<String, Stats> results, long bootMs) throws IOException {
		Path resultsDir = Path.of("..", "benchmarks", "results");
		Files.createDirectories(resultsDir);
		String date = Instant.now().toString().substring(0, 10);

		Map<String, Object> json = new LinkedHashMap<>();
		json.put("date", date);
		json.put("method", "single-threaded, 20 warmup + 200 measured, in-memory H2, JVM-local HTTP");
		json.put("boot_ms", bootMs);
		results.forEach((operation, stats) -> json.put(operation, stats.toMap()));

		Files.writeString(resultsDir.resolve("baseline-" + date + ".json"), mapper.writerWithDefaultPrettyPrinter().writeValueAsString(json),
				StandardCharsets.UTF_8);

		StringBuilder md = new StringBuilder();
		md.append("# Latency baseline — ").append(date).append("\n\n");
		md.append("Single-threaded HTTP calls against the app on in-memory H2 (JVM-local).\n");
		md.append("Method: 20 warmup, ").append(ITERATIONS).append(" measured per operation. Boot time ").append(bootMs).append(" ms.\n\n");
		md.append("| Operation | mean | p50 | p95 | p99 | max |\n");
		md.append("|-----------|------|-----|-----|-----|-----|\n");
		results.forEach((operation, stats) -> md.append(stats.row(operation)));
		md.append("\nEnvironment: ")
				.append(System.getProperty("os.name")).append(", Java ")
				.append(System.getProperty("java.version")).append(".\n");
		md.append("Rerun: `./mvnw -q -Pbenchmark exec:java` from `backend/`.\n");
		Files.writeString(resultsDir.resolve("baseline-" + date + ".md"), md.toString(), StandardCharsets.UTF_8);
	}

	/** Moved to the package-level {@link Stats} so the load harness shares it. */
}
