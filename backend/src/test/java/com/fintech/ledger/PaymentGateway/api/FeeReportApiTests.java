package com.fintech.ledger.PaymentGateway.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Integration tests for GET /api/fees: per-merchant per-currency fee totals
 * and the overall totals, built from real recorded lifecycles.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FeeReportApiTests {

	@Autowired
	private MockMvc mockMvc;

	private String registerMerchant(String name) throws Exception {
		String email = "fees-" + UUID.randomUUID().toString().substring(0, 8) + "@ledger.test";
		return mockMvc.perform(post("/api/merchants")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"businessName\": \"" + name + "\", \"email\": \"" + email + "\"}"))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
	}

	private long createPayment(String apiKey, String amount, String currency, String key) throws Exception {
		String body = mockMvc.perform(post("/api/payments")
				.header("X-API-Key", apiKey)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"amount\": \"" + amount + "\", \"currency\": \"" + currency
						+ "\", \"description\": \"fee test\", \"idempotencyKey\": \"" + key + "\"}"))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return Long.parseLong(body.replaceAll(".*\"id\":(\\d+).*", "$1"));
	}

	private void authorizeCaptureFee(String apiKey, long paymentId, String payAmount, String feeAmount) throws Exception {
		mockMvc.perform(post("/api/payments/" + paymentId + "/transactions")
				.header("X-API-Key", apiKey)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"type\": \"AUTHORIZATION\", \"amount\": \"" + payAmount + "\"}"))
				.andExpect(status().isCreated());
		mockMvc.perform(post("/api/payments/" + paymentId + "/transactions")
				.header("X-API-Key", apiKey)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"type\": \"CAPTURE\", \"amount\": \"" + payAmount + "\"}"))
				.andExpect(status().isCreated());
		mockMvc.perform(post("/api/payments/" + paymentId + "/transactions")
				.header("X-API-Key", apiKey)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"type\": \"FEE\", \"amount\": \"" + feeAmount + "\"}"))
				.andExpect(status().isCreated());
	}

	@Test
	void feesReportSumsPerMerchantAndOverall() throws Exception {
		String merchantA = registerMerchant("Fees A Co");
		String merchantB = registerMerchant("Fees B Co");
		String keyA = merchantA.replaceAll(".*\"apiKey\":\"([^\"]+)\".*", "$1");
		String keyB = merchantB.replaceAll(".*\"apiKey\":\"([^\"]+)\".*", "$1");
		String emailA = merchantA.replaceAll(".*\"email\":\"([^\"]+)\".*", "$1");

		String suffix = UUID.randomUUID().toString().substring(0, 8);

		// Merchant A: two USD payments with fees 2.50 and 1.00 -> 3.50 USD.
		authorizeCaptureFee(keyA, createPayment(keyA, "100.00", "USD", suffix + "-a1"), "100.00", "2.50");
		authorizeCaptureFee(keyA, createPayment(keyA, "50.00", "USD", suffix + "-a2"), "50.00", "1.00");

		// Merchant B: one EUR payment with fee 0.75 -> separate currency row.
		authorizeCaptureFee(keyB, createPayment(keyB, "30.00", "EUR", suffix + "-b1"), "30.00", "0.75");

		MvcResult result = mockMvc.perform(get("/api/fees"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.merchants").isArray())
				.andExpect(jsonPath("$.totals").isArray())
				.andReturn();

		String body = result.getResponse().getContentAsString();
		org.assertj.core.api.Assertions.assertThat(body)
				.contains("\"businessName\":\"Fees A Co\"")
				.contains("\"businessName\":\"Fees B Co\"")
				.contains("\"totalFees\":\"3.50\"")
				.contains("\"totalFees\":\"0.75\"");

		// Overall totals: this suite's context DB is shared across test classes,
		// so assert the currency structure and that the USD total includes this
		// test's fees (>= 4.25) rather than an exact global value.
		org.assertj.core.api.Assertions.assertThat(body)
				.contains("{\"currency\":\"EUR\",\"totalFees\":\"0.75\",\"feeCount\":1}");
		tools.jackson.databind.JsonNode totals = new tools.jackson.databind.ObjectMapper()
				.readTree(body).get("totals");
		for (tools.jackson.databind.JsonNode total : totals) {
			if ("USD".equals(total.get("currency").asText())) {
				org.assertj.core.api.Assertions.assertThat(new java.math.BigDecimal(total.get("totalFees").asText()))
						.isGreaterThanOrEqualTo(new java.math.BigDecimal("4.25"));
				org.assertj.core.api.Assertions.assertThat(total.get("feeCount").asLong()).isGreaterThanOrEqualTo(3L);
			}
		}
	}
}
