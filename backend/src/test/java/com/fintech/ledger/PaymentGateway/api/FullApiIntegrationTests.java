package com.fintech.ledger.PaymentGateway.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/**
 * End-to-end suite over real HTTP (random port, live servlet stack): every
 * endpoint, the auth filter, and the error contract in one flow.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate
@org.springframework.test.context.ActiveProfiles("test")
class FullApiIntegrationTests {

	@Autowired
	private TestRestTemplate rest;

	private static String uniqueEmail() {
		return "e2e-" + System.nanoTime() + "@ledger.test";
	}

	@Test
	void healthEndpointIsOpen() {
		ResponseEntity<String> health = rest.getForEntity("/actuator/health", String.class);
		assertThat(health.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(health.getBody()).contains("UP");
	}

	@Test
	void fullMerchantPaymentLifecycleOverHttp() {
		// Register a merchant (open endpoint).
		Map<String, String> registerBody = Map.of("businessName", "E2E Trading", "email", uniqueEmail());
		ResponseEntity<Map> registerResponse = rest.postForEntity("/api/merchants",
				jsonEntity(registerBody), Map.class);
		assertThat(registerResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		@SuppressWarnings("unchecked")
		Map<String, Object> merchant = registerResponse.getBody();
		String apiKey = (String) merchant.get("apiKey");
		assertThat(apiKey).startsWith("sk_");

		// Duplicate email conflicts.
		ResponseEntity<String> duplicate = rest.postForEntity("/api/merchants",
				jsonEntity(registerBody), String.class);
		assertThat(duplicate.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
		assertThat(duplicate.getBody()).contains("\"status\":409").contains("timestamp");

		// Payment endpoints reject unauthenticated calls with the standard body.
		ResponseEntity<String> unauth = rest.postForEntity("/api/payments",
				jsonEntity(Map.of("amount", "10.00", "currency", "USD", "description", "x", "idempotencyKey", "e2e-noauth")),
				String.class);
		assertThat(unauth.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
		assertThat(unauth.getBody()).contains("\"status\":401").contains("message");

		// Create a payment.
		HttpHeaders authed = new HttpHeaders();
		authed.set("X-API-Key", apiKey);
		Map<String, String> paymentBody = Map.of(
				"amount", "120.00",
				"currency", "USD",
				"description", "E2E invoice",
				"idempotencyKey", "e2e-key-" + System.nanoTime());
		ResponseEntity<Map> created = rest.exchange("/api/payments", HttpMethod.POST,
				new HttpEntity<>(paymentBody, authed), Map.class);
		assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		@SuppressWarnings("unchecked")
		Map<String, Object> payment = created.getBody();
		long paymentId = ((Number) payment.get("id")).longValue();
		assertThat(payment.get("status")).isEqualTo("REQUIRES_PAYMENT");

		// Replay with the same idempotency key returns 200 and the same payment.
		ResponseEntity<Map> replay = rest.exchange("/api/payments", HttpMethod.POST,
				new HttpEntity<>(paymentBody, authed), Map.class);
		assertThat(replay.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(((Number) replay.getBody().get("id")).longValue()).isEqualTo(paymentId);

		// Walk the ledger: authorize, capture, refund.
		recordAndExpect(authed, paymentId, "AUTHORIZATION", "120.00", HttpStatus.CREATED);
		recordAndExpect(authed, paymentId, "CAPTURE", "120.00", HttpStatus.CREATED);
		recordAndExpect(authed, paymentId, "REFUND", "20.00", HttpStatus.CREATED);

		// Illegal transition conflicts with the standard body.
		ResponseEntity<String> illegal = rest.exchange("/api/payments/" + paymentId + "/transactions",
				HttpMethod.POST, new HttpEntity<>(txBody("CAPTURE", "120.00"), authed), String.class);
		assertThat(illegal.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
		assertThat(illegal.getBody()).contains("cannot capture a payment in status REFUNDED");

		// Detail shows the full ledger and net captured amount.
		ResponseEntity<Map> detail = rest.exchange("/api/payments/" + paymentId, HttpMethod.GET,
				new HttpEntity<>(authed), Map.class);
		assertThat(detail.getStatusCode()).isEqualTo(HttpStatus.OK);
		@SuppressWarnings("unchecked")
		Map<String, Object> detailBody = detail.getBody();
		assertThat(detailBody.get("status")).isEqualTo("REFUNDED");
		assertThat((String) detailBody.get("capturedAmountInUsd")).isEqualTo("100.00");
		assertThat((java.util.List<?>) detailBody.get("transactions")).hasSize(3);

		// Unknown ids 404 with the standard body.
		ResponseEntity<String> missing = rest.exchange("/api/payments/987654321", HttpMethod.GET,
				new HttpEntity<>(authed), String.class);
		assertThat(missing.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
		assertThat(missing.getBody()).contains("\"status\":404").contains("timestamp");

		// Status filter works over HTTP.
		ResponseEntity<String> filtered = rest.exchange("/api/payments?status=REFUNDED", HttpMethod.GET,
				new HttpEntity<>(authed), String.class);
		assertThat(filtered.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(filtered.getBody()).contains("REFUNDED");
	}

	@Test
	void malformedAmountReturns400OverHttp() {
		Map<String, String> registerBody = Map.of("businessName", "E2E Malformed", "email", uniqueEmail());
		String apiKey = (String) rest.postForEntity("/api/merchants", jsonEntity(registerBody), Map.class)
				.getBody().get("apiKey");
		HttpHeaders authed = new HttpHeaders();
		authed.set("X-API-Key", apiKey);

		ResponseEntity<Map> created = rest.exchange("/api/payments", HttpMethod.POST,
				new HttpEntity<>(Map.of("amount", "50.00", "currency", "USD", "description", "d",
						"idempotencyKey", "e2e-malformed-" + System.nanoTime()), authed), Map.class);
		long paymentId = ((Number) created.getBody().get("id")).longValue();

		ResponseEntity<String> bad = rest.exchange("/api/payments/" + paymentId + "/transactions",
				HttpMethod.POST, new HttpEntity<>(txBody("AUTHORIZATION", "0.1"), authed), String.class);
		assertThat(bad.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(bad.getBody()).contains("two decimal places");
	}

	private void recordAndExpect(HttpHeaders authed, long paymentId, String type, String amount,
			HttpStatus expected) {
		ResponseEntity<Map> response = rest.exchange("/api/payments/" + paymentId + "/transactions",
				HttpMethod.POST, new HttpEntity<>(txBody(type, amount), authed), Map.class);
		assertThat(response.getStatusCode()).isEqualTo(expected);
		assertThat(response.getBody().get("type")).isEqualTo(type);
		assertThat(response.getBody().get("amount")).isEqualTo(amount);
	}

	private static Map<String, String> txBody(String type, String amount) {
		return Map.of("type", type, "amount", amount);
	}

	private static HttpEntity<Map<String, String>> jsonEntity(Map<String, String> body) {
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		return new HttpEntity<>(body, headers);
	}
}
