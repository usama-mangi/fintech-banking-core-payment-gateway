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
	void suspendReactivatesAndClosesMerchantWithLockout() throws Exception {
		String created = mockMvc.perform(post("/api/merchants")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"businessName\": \"Lockout Co\", \"email\": \"ops@lockout-api.test\"}"))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		long id = Long.parseLong(created.replaceAll(".*\"id\":(\\d+).*", "$1"));
		String apiKey = created.replaceAll(".*\"apiKey\":\"([^\"]+)\".*", "$1");

		// Active merchant can create payments.
		mockMvc.perform(post("/api/payments")
				.header("X-API-Key", apiKey)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"amount\": \"5.00\", \"currency\": \"USD\", \"description\": \"x\", \"idempotencyKey\": \"lockout-1\"}"))
				.andExpect(status().isCreated());

		// Double suspend is a conflict.
		mockMvc.perform(post("/api/merchants/" + id + "/suspend")).andExpect(status().isOk());
		mockMvc.perform(post("/api/merchants/" + id + "/suspend"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.status").value(409));

		// Suspended merchant is locked out of the payment API immediately.
		mockMvc.perform(post("/api/payments")
				.header("X-API-Key", apiKey)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"amount\": \"5.00\", \"currency\": \"USD\", \"description\": \"x\", \"idempotencyKey\": \"lockout-2\"}"))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.message").value("merchant is suspended"));

		// Reactivate restores access.
		mockMvc.perform(post("/api/merchants/" + id + "/reactivate"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("ACTIVE"));
		mockMvc.perform(post("/api/payments")
				.header("X-API-Key", apiKey)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"amount\": \"5.00\", \"currency\": \"USD\", \"description\": \"x\", \"idempotencyKey\": \"lockout-3\"}"))
				.andExpect(status().isCreated());

		// Close is terminal: every further transition 409s.
		mockMvc.perform(post("/api/merchants/" + id + "/close")).andExpect(status().isOk());
		mockMvc.perform(post("/api/merchants/" + id + "/suspend")).andExpect(status().isConflict());
		mockMvc.perform(post("/api/merchants/" + id + "/reactivate")).andExpect(status().isConflict());
		mockMvc.perform(post("/api/merchants/" + id + "/close")).andExpect(status().isConflict());
	}

	@Test
	void lifecycleActionOnUnknownMerchantReturns404() throws Exception {
		mockMvc.perform(post("/api/merchants/999999/suspend")).andExpect(status().isNotFound());
		mockMvc.perform(post("/api/merchants/999999/reactivate")).andExpect(status().isNotFound());
		mockMvc.perform(post("/api/merchants/999999/close")).andExpect(status().isNotFound());
	}

	@Test
	void unknownEndpointReturns404Not500() throws Exception {
		mockMvc.perform(post("/api/merchants/1/no-such-action"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.status").value(404))
				.andExpect(jsonPath("$.error").value("Not Found"))
				.andExpect(jsonPath("$.message").isNotEmpty());
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
