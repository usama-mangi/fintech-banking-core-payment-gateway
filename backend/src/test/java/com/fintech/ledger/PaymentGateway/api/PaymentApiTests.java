package com.fintech.ledger.PaymentGateway.api;

import static org.hamcrest.Matchers.hasSize;
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
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.transaction.support.TransactionTemplate;

import com.fintech.ledger.PaymentGateway.domain.Merchant;
import com.fintech.ledger.PaymentGateway.domain.MerchantStatus;
import com.fintech.ledger.PaymentGateway.repository.MerchantRepository;

/**
 * Integration tests for the payment and ledger REST API behind the API-key
 * filter.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PaymentApiTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private MerchantRepository merchantRepository;

	@Autowired
	private TransactionTemplate transactionTemplate;

	/** Registers a merchant with a fresh key and returns the key. */
	private String registerMerchant(MerchantStatus status) {
		String suffix = UUID.randomUUID().toString().substring(0, 8);
		return transactionTemplate.execute(status1 -> {
			Merchant merchant = new Merchant("Ledger Co " + suffix, "ops-" + suffix + "@ledger.test", "sk_test_" + suffix);
			merchant.setStatus(status);
			return merchantRepository.save(merchant).getApiKey();
		});
	}

	private String paymentBody(String key) {
		return """
				{"amount": "100.00", "currency": "USD", "description": "Invoice #7", "idempotencyKey": "%s"}
				""".formatted(key);
	}

	private String paymentBodyWithMerchant(long merchantId, String key) {
		return """
				{"merchantId": %d, "amount": "100.00", "currency": "USD", "description": "Invoice #7", "idempotencyKey": "%s"}
				""".formatted(merchantId, key);
	}

	@Test
	void createPaymentWithoutKeyReturns401() throws Exception {
		mockMvc.perform(post("/api/payments")
				.contentType(MediaType.APPLICATION_JSON)
				.content(paymentBody("key-no-auth")))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.status").value(401))
				.andExpect(jsonPath("$.message").isNotEmpty())
				.andExpect(jsonPath("$.timestamp").isNotEmpty());
	}

	@Test
	void createPaymentWithUnknownKeyReturns401() throws Exception {
		mockMvc.perform(post("/api/payments")
				.header("X-API-Key", "sk_does_not_exist")
				.contentType(MediaType.APPLICATION_JSON)
				.content(paymentBody("key-unknown-auth")))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void suspendedMerchantKeyReturns403() throws Exception {
		String apiKey = registerMerchant(MerchantStatus.SUSPENDED);

		mockMvc.perform(post("/api/payments")
				.header("X-API-Key", apiKey)
				.contentType(MediaType.APPLICATION_JSON)
				.content(paymentBody("key-suspended")))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.status").value(403));
	}

	@Test
	void createPaymentReturns201AndReplayReturns200SameId() throws Exception {
		String apiKey = registerMerchant(MerchantStatus.ACTIVE);

		String first = mockMvc.perform(post("/api/payments")
				.header("X-API-Key", apiKey)
				.contentType(MediaType.APPLICATION_JSON)
				.content(paymentBody("key-replay-1")))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.status").value("REQUIRES_PAYMENT"))
				.andExpect(jsonPath("$.amount").value("100.00"))
				.andReturn().getResponse().getContentAsString();
		long paymentId = Long.parseLong(first.replaceAll(".*\"id\":(\\d+).*", "$1"));

		mockMvc.perform(post("/api/payments")
				.header("X-API-Key", apiKey)
				.contentType(MediaType.APPLICATION_JSON)
				.content(paymentBody("key-replay-1")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(paymentId));
	}

	@Test
	void bodyMerchantIdOfAnotherMerchantReturns403() throws Exception {
		String apiKey = registerMerchant(MerchantStatus.ACTIVE);
		long otherMerchantId = transactionTemplate.execute(status ->
				merchantRepository.findByApiKey(registerMerchant(MerchantStatus.ACTIVE)).orElseThrow().getId());

		mockMvc.perform(post("/api/payments")
				.header("X-API-Key", apiKey)
				.contentType(MediaType.APPLICATION_JSON)
				.content(paymentBodyWithMerchant(otherMerchantId, "key-impersonation")))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.message").isNotEmpty());
	}

	@Test
	void authorizeCaptureRefundLifecycleEndsRefundedWithOrderedLedger() throws Exception {
		String apiKey = registerMerchant(MerchantStatus.ACTIVE);

		String body = mockMvc.perform(post("/api/payments")
				.header("X-API-Key", apiKey)
				.contentType(MediaType.APPLICATION_JSON)
				.content(paymentBody("key-lifecycle-2")))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		long paymentId = Long.parseLong(body.replaceAll(".*\"id\":(\\d+).*", "$1"));

		recordTransaction(apiKey, paymentId, "AUTHORIZATION", "100.00", status().isCreated());
		recordTransaction(apiKey, paymentId, "CAPTURE", "100.00", status().isCreated());
		recordTransaction(apiKey, paymentId, "REFUND", "30.00", status().isCreated());

		mockMvc.perform(get("/api/payments/" + paymentId).header("X-API-Key", apiKey))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("REFUNDED"))
				.andExpect(jsonPath("$.capturedAmountInUsd").value("70.00"))
				.andExpect(jsonPath("$.transactions", hasSize(3)))
				.andExpect(jsonPath("$.transactions[0].type").value("AUTHORIZATION"))
				.andExpect(jsonPath("$.transactions[2].type").value("REFUND"));
	}

	@Test
	void captureBeforeAuthorizeReturns409() throws Exception {
		String apiKey = registerMerchant(MerchantStatus.ACTIVE);
		long paymentId = createPayment(apiKey, "key-conflict-2");

		recordTransaction(apiKey, paymentId, "CAPTURE", "100.00", status().isConflict());
	}

	@Test
	void refundBeforeCaptureReturns409() throws Exception {
		String apiKey = registerMerchant(MerchantStatus.ACTIVE);
		long paymentId = createPayment(apiKey, "key-refund-early-2");

		recordTransaction(apiKey, paymentId, "REFUND", "30.00", status().isConflict());
	}

	@Test
	void unknownPaymentTransactionPostReturns404() throws Exception {
		String apiKey = registerMerchant(MerchantStatus.ACTIVE);
		recordTransaction(apiKey, 987654, "AUTHORIZATION", "10.00", status().isNotFound());
	}

	@Test
	void malformedAmountReturns400() throws Exception {
		String apiKey = registerMerchant(MerchantStatus.ACTIVE);
		long paymentId = createPayment(apiKey, "key-malformed-2");

		mockMvc.perform(post("/api/payments/" + paymentId + "/transactions")
				.header("X-API-Key", apiKey)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"type": "AUTHORIZATION", "amount": "abc"}
						"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").isNotEmpty());
	}

	@Test
	void listFiltersByStatus() throws Exception {
		String apiKey = registerMerchant(MerchantStatus.ACTIVE);
		long paymentId = createPayment(apiKey, "key-filter-3");

		mockMvc.perform(get("/api/payments").param("status", "REQUIRES_PAYMENT").header("X-API-Key", apiKey))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[?(@.id == " + paymentId + ")]").isNotEmpty());

		recordTransaction(apiKey, paymentId, "AUTHORIZATION", "100.00", status().isCreated());

		mockMvc.perform(get("/api/payments").param("status", "REQUIRES_PAYMENT").header("X-API-Key", apiKey))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[?(@.id == " + paymentId + ")]").isEmpty());
	}

	private long createPayment(String apiKey, String idempotencyKey) throws Exception {
		String response = mockMvc.perform(post("/api/payments")
				.header("X-API-Key", apiKey)
				.contentType(MediaType.APPLICATION_JSON)
				.content(paymentBody(idempotencyKey)))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return Long.parseLong(response.replaceAll(".*\"id\":(\\d+).*", "$1"));
	}

	private void recordTransaction(String apiKey, long paymentId, String type, String amount, ResultMatcher matcher)
			throws Exception {
		mockMvc.perform(post("/api/payments/" + paymentId + "/transactions")
				.header("X-API-Key", apiKey)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"type": "%s", "amount": "%s"}
						""".formatted(type, amount)))
				.andExpect(matcher);
	}
}
