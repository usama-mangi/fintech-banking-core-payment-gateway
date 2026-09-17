package com.fintech.ledger.PaymentGateway.api;

import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Integration tests for the merchant REST API. Full context plus MockMvc, so
 * mapping, validation, serialization and persistence are exercised together.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MerchantApiTests {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void postMerchantReturns201WithGeneratedApiKey() throws Exception {
		mockMvc.perform(post("/api/merchants")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"businessName": "Acme Corp", "email": "ops@acme-api.test"}
						"""))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").isNumber())
				.andExpect(jsonPath("$.apiKey").value(startsWith("sk_")))
				.andExpect(jsonPath("$.status").value("ACTIVE"));
	}

	@Test
	void postDuplicateEmailReturns409WithErrorShape() throws Exception {
		String body = """
				{"businessName": "Beta LLC", "email": "dupe@beta-api.test"}
				""";
		mockMvc.perform(post("/api/merchants").contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isCreated());
		mockMvc.perform(post("/api/merchants").contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.status").value(409))
				.andExpect(jsonPath("$.error").value("Conflict"))
				.andExpect(jsonPath("$.message").isNotEmpty())
				.andExpect(jsonPath("$.timestamp").isNotEmpty());
	}

	@Test
	void getUnknownMerchantReturns404WithErrorShape() throws Exception {
		mockMvc.perform(get("/api/merchants/999999"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.status").value(404))
				.andExpect(jsonPath("$.error").value("Not Found"))
				.andExpect(jsonPath("$.message").isNotEmpty())
				.andExpect(jsonPath("$.timestamp").isNotEmpty());
	}

	@Test
	void getMerchantsListAndStatusFilter() throws Exception {
		mockMvc.perform(post("/api/merchants")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"businessName": "Gamma Co", "email": "ops@gamma-api.test"}
						"""))
				.andExpect(status().isCreated());

		mockMvc.perform(get("/api/merchants").param("status", "ACTIVE"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content").isArray())
				.andExpect(jsonPath("$.content[?(@.email == 'ops@gamma-api.test')]").isNotEmpty());
	}

	@Test
	void postInvalidBodyReturns400() throws Exception {
		mockMvc.perform(post("/api/merchants")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"businessName": "  ", "email": "not-an-email"}
						"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.status").value(400))
				.andExpect(jsonPath("$.error").value("Bad Request"))
				.andExpect(jsonPath("$.message").isNotEmpty());
	}
}
