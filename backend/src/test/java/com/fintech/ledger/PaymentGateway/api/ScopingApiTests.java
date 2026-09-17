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
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

import com.fintech.ledger.PaymentGateway.domain.Merchant;
import com.fintech.ledger.PaymentGateway.repository.MerchantRepository;

/**
 * Integration tests for API scoping (TASK-14): merchant keys see only their
 * own payments; the internal key sees everything; /api/fees is internal-only.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = "internal.api.key=sk_internal_test_key")
class ScopingApiTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private MerchantRepository merchantRepository;

	@Autowired
	private TransactionTemplate transactionTemplate;

	private static final String INTERNAL_KEY = "sk_internal_test_key";

	private String registerMerchant(String name) throws Exception {
		String email = "scope-" + UUID.randomUUID().toString().substring(0, 8) + "@ledger.test";
		return mockMvc.perform(post("/api/merchants")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"businessName\": \"" + name + "\", \"email\": \"" + email + "\"}"))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
	}

	private long createPayment(String apiKey, String key) throws Exception {
		String body = mockMvc.perform(post("/api/payments")
				.header("X-API-Key", apiKey)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"amount\": \"10.00\", \"currency\": \"USD\", \"description\": \"scope\", \"idempotencyKey\": \""
						+ key + "\"}"))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return Long.parseLong(body.replaceAll(".*\"id\":(\\d+).*", "$1"));
	}

	@Test
	void merchantKeyIsForcedToOwnPaymentsOnList() throws Exception {
		String merchantA = registerMerchant("Scope A Co");
		String merchantB = registerMerchant("Scope B Co");
		String keyA = merchantA.replaceAll(".*\"apiKey\":\"([^\"]+)\".*", "$1");
		String keyB = merchantB.replaceAll(".*\"apiKey\":\"([^\"]+)\".*", "$1");
		long idB = Long.parseLong(merchantB.replaceAll(".*\"id\":(\\d+).*", "$1"));

		long paymentA = createPayment(keyA, "scope-a-" + UUID.randomUUID().toString().substring(0, 8));
		createPayment(keyB, "scope-b-" + UUID.randomUUID().toString().substring(0, 8));

		// Merchant A's list contains only A's payment, even though B's exists.
		String list = mockMvc.perform(get("/api/payments").header("X-API-Key", keyA))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(1))
				.andReturn().getResponse().getContentAsString();
		org.assertj.core.api.Assertions.assertThat(list).contains(String.valueOf(paymentA));

		// Asking for another merchant's id explicitly is a 403.
		mockMvc.perform(get("/api/payments").param("merchantId", String.valueOf(idB)).header("X-API-Key", keyA))
				.andExpect(status().isForbidden());
	}

	@Test
	void merchantKeyOnAnotherMerchantsPaymentGets404() throws Exception {
		String keyA = registerMerchant("Scope C Co").replaceAll(".*\"apiKey\":\"([^\"]+)\".*", "$1");
		String keyB = registerMerchant("Scope D Co").replaceAll(".*\"apiKey\":\"([^\"]+)\".*", "$1");

		long paymentB = createPayment(keyB, "scope-d-" + UUID.randomUUID().toString().substring(0, 8));

		// 404, not 403: existence of B's payment is not disclosed to A.
		mockMvc.perform(get("/api/payments/" + paymentB).header("X-API-Key", keyA))
				.andExpect(status().isNotFound());

		// The owner can read it fine.
		mockMvc.perform(get("/api/payments/" + paymentB).header("X-API-Key", keyB))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(paymentB));
	}

	@Test
	void internalKeySeesEverythingIncludingOtherMerchantsPayments() throws Exception {
		String keyB = registerMerchant("Scope E Co").replaceAll(".*\"apiKey\":\"([^\"]+)\".*", "$1");
		long paymentB = createPayment(keyB, "scope-e-" + UUID.randomUUID().toString().substring(0, 8));

		mockMvc.perform(get("/api/payments/" + paymentB).header("X-API-Key", INTERNAL_KEY))
				.andExpect(status().isOk());

		String list = mockMvc.perform(get("/api/payments").header("X-API-Key", INTERNAL_KEY))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		org.assertj.core.api.Assertions.assertThat(list).contains(String.valueOf(paymentB));

		// The internal key can also filter by an arbitrary merchant.
		long merchantBId = transactionTemplate.execute(status ->
				merchantRepository.findByApiKey(keyB).orElseThrow().getId());
		mockMvc.perform(get("/api/payments").param("merchantId", String.valueOf(merchantBId))
				.header("X-API-Key", INTERNAL_KEY))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(1));
	}

	@Test
	void feesEndpointRejectsMerchantKeysButAcceptsInternalKey() throws Exception {
		String keyA = registerMerchant("Scope F Co").replaceAll(".*\"apiKey\":\"([^\"]+)\".*", "$1");

		mockMvc.perform(get("/api/fees").header("X-API-Key", keyA))
				.andExpect(status().isForbidden());

		mockMvc.perform(get("/api/fees").header("X-API-Key", INTERNAL_KEY))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.merchants").isArray());

		mockMvc.perform(get("/api/fees"))
				.andExpect(status().isUnauthorized());
	}
}
