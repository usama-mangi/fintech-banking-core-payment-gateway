package com.fintech.ledger.PaymentGateway.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Integration tests for GET /api/payments/export: CSV shape, scoping and
 * filter composition.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = "internal.api.key=sk_internal_export_tests")
class ExportApiTests {

	private static final String INTERNAL_KEY = "sk_internal_export_tests";

	@Autowired
	private MockMvc mockMvc;

	private String registerMerchant(String name) throws Exception {
		String email = "csv-" + UUID.randomUUID().toString().substring(0, 8) + "@ledger.test";
		return mockMvc.perform(post("/api/merchants")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"businessName\": \"" + name + "\", \"email\": \"" + email + "\"}"))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
	}

	private long createPayment(String apiKey, String description, String key) throws Exception {
		String body = mockMvc.perform(post("/api/payments")
				.header("X-API-Key", apiKey)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"amount\": \"10.00\", \"currency\": \"USD\", \"description\": \"" + description
						+ "\", \"idempotencyKey\": \"" + key + "\"}"))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return Long.parseLong(body.replaceAll(".*\"id\":(\\d+).*", "$1"));
	}

	@Test
	void exportReturnsCsvHeaderAndRowsForOwnPaymentsOnly() throws Exception {
		String merchantA = registerMerchant("Csv A Co");
		String merchantB = registerMerchant("Csv B Co");
		String keyA = merchantA.replaceAll(".*\"apiKey\":\"([^\"]+)\".*", "$1");
		String keyB = merchantB.replaceAll(".*\"apiKey\":\"([^\"]+)\".*", "$1");
		long idB = Long.parseLong(merchantB.replaceAll(".*\"id\":(\\d+).*", "$1"));

		String suffix = UUID.randomUUID().toString().substring(0, 8);
		long paymentA = createPayment(keyA, "plain description", suffix + "-a");
		createPayment(keyB, "B only", suffix + "-b");

		MvcResult result = mockMvc.perform(get("/api/payments/export").header("X-API-Key", keyA))
				.andExpect(status().isOk())
				.andReturn();

		String csv = result.getResponse().getContentAsString();
		org.assertj.core.api.Assertions.assertThat(result.getResponse().getContentType())
				.startsWith("text/csv");
		org.assertj.core.api.Assertions.assertThat(csv)
				.startsWith("id,merchant_id,amount,currency,status,description,idempotency_key,created_at")
				.contains(paymentA + ",")
				.contains("plain description");
		// Scoped: no rows from merchant B.
		for (String line : csv.lines().toList()) {
			if (line.startsWith("id,")) {
				continue;
			}
			org.assertj.core.api.Assertions.assertThat(Long.parseLong(line.split(",")[1]))
					.isNotEqualTo(idB);
		}
	}

	@Test
	void exportEscapesQuotedDescriptions() throws Exception {
		String keyA = registerMerchant("Csv C Co").replaceAll(".*\"apiKey\":\"([^\"]+)\".*", "$1");
		// JSON-escaped description containing a comma and quotes; the CSV layer
		// must re-escape it per RFC 4180.
		mockMvc.perform(post("/api/payments")
				.header("X-API-Key", keyA)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"amount\": \"10.00\", \"currency\": \"USD\", \"description\": \"tricky, \\\"quoted\\\" text\", \"idempotencyKey\": \"csv-esc-"
						+ UUID.randomUUID().toString().substring(0, 8) + "\"}"))
				.andExpect(status().isCreated());

		MvcResult result = mockMvc.perform(get("/api/payments/export").header("X-API-Key", keyA))
				.andExpect(status().isOk())
				.andReturn();
		String csv = result.getResponse().getContentAsString();

		org.assertj.core.api.Assertions.assertThat(csv)
				.contains("\"tricky, \"\"quoted\"\" text\"");
	}

	@Test
	void exportScopedLikeList() throws Exception {
		String keyA = registerMerchant("Csv D Co").replaceAll(".*\"apiKey\":\"([^\"]+)\".*", "$1");
		String keyB = registerMerchant("Csv E Co").replaceAll(".*\"apiKey\":\"([^\"]+)\".*", "$1");
		long paymentB = createPayment(keyB, "B row", "csv-e-" + UUID.randomUUID().toString().substring(0, 8));

		// Internal key sees B's payment in the export.
		String internalCsv = mockMvc.perform(get("/api/payments/export").header("X-API-Key", INTERNAL_KEY))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		org.assertj.core.api.Assertions.assertThat(internalCsv).contains(String.valueOf(paymentB));

		// Merchant A cannot export another merchant's rows.
		String ownCsv = mockMvc.perform(get("/api/payments/export").header("X-API-Key", keyA))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		org.assertj.core.api.Assertions.assertThat(ownCsv).doesNotContain(String.valueOf(paymentB) + ",");

		// Explicit other merchantId -> 403, matching list behavior.
		mockMvc.perform(get("/api/payments/export").param("merchantId", "999999").header("X-API-Key", keyA))
				.andExpect(status().isForbidden());
	}
}
