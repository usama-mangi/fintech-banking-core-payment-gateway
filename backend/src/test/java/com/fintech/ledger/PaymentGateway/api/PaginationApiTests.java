package com.fintech.ledger.PaymentGateway.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
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
 * Integration tests for the paginated list envelope on /api/merchants and
 * /api/payments: envelope metadata, deterministic newest-first ordering,
 * filters combined with paging, and page/size validation.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PaginationApiTests {

	@Autowired
	private MockMvc mockMvc;

	private String registerMerchant(String businessName, String email) throws Exception {
		return mockMvc.perform(post("/api/merchants")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"businessName\": \"" + businessName + "\", \"email\": \"" + email + "\"}"))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
	}

	private long createPayment(String apiKey, String idempotencyKey) throws Exception {
		String body = mockMvc.perform(post("/api/payments")
				.header("X-API-Key", apiKey)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"amount\": \"100.00\", \"currency\": \"USD\", \"description\": \"Invoice\", \"idempotencyKey\": \""
						+ idempotencyKey + "\"}"))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return Long.parseLong(body.replaceAll(".*\"id\":(\\d+).*", "$1"));
	}

	@Test
	void merchantsListReturnsEnvelopeNotBareArray() throws Exception {
		String email = "env-" + UUID.randomUUID().toString().substring(0, 8) + "@pag.test";
		registerMerchant("Envelope Co", email);

		MvcResult result = mockMvc.perform(get("/api/merchants"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content").isArray())
				.andExpect(jsonPath("$.page").value(0))
				.andExpect(jsonPath("$.size").value(20))
				.andExpect(jsonPath("$.totalElements").isNumber())
				.andExpect(jsonPath("$.totalPages").isNumber())
				.andExpect(jsonPath("$.content[?(@.email == '" + email + "')]").isNotEmpty())
				.andReturn();

		assertThat(result.getResponse().getContentAsString()).startsWith("{");
	}

	@Test
	void merchantsPagingSplitsAcrossPagesNewestFirst() throws Exception {
		String prefix = "split-" + UUID.randomUUID().toString().substring(0, 6);
		List<String> emails = new ArrayList<>();
		for (int i = 0; i < 3; i++) {
			String email = prefix + i + "@pag.test";
			emails.add(email);
			registerMerchant("Page Co " + i, email);
			Thread.sleep(5);
		}

		String firstPage = mockMvc.perform(get("/api/merchants").param("page", "0").param("size", "2"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.page").value(0))
				.andExpect(jsonPath("$.size").value(2))
				.andExpect(jsonPath("$.content[0].email").value(emails.get(2)))
				.andExpect(jsonPath("$.content[1].email").value(emails.get(1)))
				.andReturn().getResponse().getContentAsString();

		String secondPage = mockMvc.perform(get("/api/merchants").param("page", "1").param("size", "2"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.page").value(1))
				.andExpect(jsonPath("$.content[0].email").value(emails.get(0)))
				.andReturn().getResponse().getContentAsString();

		assertThat(firstPage).contains(emails.get(2)).contains(emails.get(1));
		assertThat(secondPage).contains(emails.get(0));
	}

	@Test
	void merchantsBeyondLastPageReturnsEmptyContent() throws Exception {
		mockMvc.perform(get("/api/merchants").param("page", "999").param("size", "20"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content").isEmpty())
				.andExpect(jsonPath("$.totalElements").isNumber());
	}

	@Test
	void paymentsFilteredPagingSplitsExactTotals() throws Exception {
		String email = "pay-" + UUID.randomUUID().toString().substring(0, 8) + "@pag.test";
		String merchantBody = registerMerchant("Exact Totals Co", email);
		String apiKey = merchantBody.replaceAll(".*\"apiKey\":\"([^\"]+)\".*", "$1");
		long merchantId = Long.parseLong(merchantBody.replaceAll(".*\"id\":(\\d+).*", "$1"));

		List<Long> ids = new ArrayList<>();
		for (int i = 0; i < 3; i++) {
			ids.add(createPayment(apiKey, "pag-exact-" + UUID.randomUUID()));
		}

		mockMvc.perform(get("/api/payments")
				.param("merchantId", String.valueOf(merchantId))
				.param("status", "REQUIRES_PAYMENT")
				.param("page", "0")
				.param("size", "2")
				.header("X-API-Key", apiKey))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(3))
				.andExpect(jsonPath("$.totalPages").value(2))
				.andExpect(jsonPath("$.content.length()").value(2))
				.andExpect(jsonPath("$.content[0].id").value(ids.get(2)))
				.andExpect(jsonPath("$.content[1].id").value(ids.get(1)));

		mockMvc.perform(get("/api/payments")
				.param("merchantId", String.valueOf(merchantId))
				.param("status", "REQUIRES_PAYMENT")
				.param("page", "1")
				.param("size", "2")
				.header("X-API-Key", apiKey))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(3))
				.andExpect(jsonPath("$.content.length()").value(1))
				.andExpect(jsonPath("$.content[0].id").value(ids.get(0)));
	}

	@Test
	void invalidPageSizeParamsReturn400() throws Exception {
		String email = "auth4validation-" + UUID.randomUUID().toString().substring(0, 8) + "@pag.test";
		String merchantBody = registerMerchant("Validation Co", email);
		String apiKey = merchantBody.replaceAll(".*\"apiKey\":\"([^\"]+)\".*", "$1");

		mockMvc.perform(get("/api/payments").param("size", "0").header("X-API-Key", apiKey))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.status").value(400))
				.andExpect(jsonPath("$.message").value("size must be at least 1"));

		mockMvc.perform(get("/api/payments").param("page", "-1").header("X-API-Key", apiKey))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("page must be 0 or greater"));

		mockMvc.perform(get("/api/payments").param("size", "101").header("X-API-Key", apiKey))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("size must not exceed 100"));

		mockMvc.perform(get("/api/merchants").param("size", "0"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("size must be at least 1"));

		mockMvc.perform(get("/api/merchants").param("page", "abc"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("invalid value for parameter 'page': abc"));

		mockMvc.perform(get("/api/payments").param("size", "xx").header("X-API-Key", apiKey))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("invalid value for parameter 'size': xx"));
	}
}
