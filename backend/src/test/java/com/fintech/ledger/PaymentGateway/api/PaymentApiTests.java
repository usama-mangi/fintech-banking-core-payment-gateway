package com.fintech.ledger.PaymentGateway.api;

import static org.hamcrest.Matchers.hasSize;
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
import org.springframework.transaction.support.TransactionTemplate;

import com.fintech.ledger.PaymentGateway.domain.Merchant;
import com.fintech.ledger.PaymentGateway.repository.MerchantRepository;

/**
 * Integration tests for the payment and ledger REST API.
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

	private long createMerchant(String email) {
		return transactionTemplate.execute(status -> {
			Merchant merchant = new Merchant("Ledger Co", email, "sk_test_" + email);
			return merchantRepository.save(merchant).getId();
		});
	}

	private String paymentBody(long merchantId, String key) {
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
	void createPaymentWithoutMerchantHeaderReturns400() throws Exception {
		mockMvc.perform(post("/api/payments")
				.contentType(MediaType.APPLICATION_JSON)
				.content(paymentBody(0, "key-no-header")))
				.andExpect(status().isBadRequest());
	}

	@Test
	void createPaymentWithHeaderReturns201AndReplayReturns200SameId() throws Exception {
		long merchantId = createMerchant("pay-header@ledger.test");

		String first = mockMvc.perform(post("/api/payments")
				.header("Merchant-Id", merchantId)
				.contentType(MediaType.APPLICATION_JSON)
				.content(paymentBody(merchantId, "key-header-1")))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.status").value("REQUIRES_PAYMENT"))
				.andExpect(jsonPath("$.amount").value("100.00"))
				.andReturn().getResponse().getContentAsString();
		long paymentId = Long.parseLong(first.replaceAll(".*\"id\":(\\d+).*", "$1"));

		mockMvc.perform(post("/api/payments")
				.header("Merchant-Id", merchantId)
				.contentType(MediaType.APPLICATION_JSON)
				.content(paymentBody(merchantId, "key-header-1")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(paymentId));
	}

	@Test
	void authorizeCaptureRefundLifecycleEndsRefundedWithOrderedLedger() throws Exception {
		long merchantId = createMerchant("lifecycle@ledger.test");

		String body = mockMvc.perform(post("/api/payments")
				.header("Merchant-Id", merchantId)
				.contentType(MediaType.APPLICATION_JSON)
				.content(paymentBody(merchantId, "key-lifecycle-1")))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		long paymentId = Long.parseLong(body.replaceAll(".*\"id\":(\\d+).*", "$1"));

		recordTransaction(paymentId, "AUTHORIZATION", "100.00", isCreated());
		recordTransaction(paymentId, "CAPTURE", "100.00", isCreated());
		recordTransaction(paymentId, "REFUND", "30.00", isCreated());

		mockMvc.perform(get("/api/payments/" + paymentId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("REFUNDED"))
				.andExpect(jsonPath("$.capturedAmountInUsd").value("70.00"))
				.andExpect(jsonPath("$.transactions", hasSize(3)))
				.andExpect(jsonPath("$.transactions[0].type").value("AUTHORIZATION"))
				.andExpect(jsonPath("$.transactions[2].type").value("REFUND"));
	}

	private org.springframework.test.web.servlet.ResultMatcher isCreated() {
		return status().isCreated();
	}

	private void recordTransaction(long paymentId, String type, String amount,
			org.springframework.test.web.servlet.ResultMatcher matcher) throws Exception {
		mockMvc.perform(post("/api/payments/" + paymentId + "/transactions")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"type": "%s", "amount": "%s"}
						""".formatted(type, amount)))
				.andExpect(matcher);
	}

	@Test
	void captureBeforeAuthorizeReturns409() throws Exception {
		long merchantId = createMerchant("conflict@ledger.test");
		long paymentId = createPaymentViaApi(merchantId, "key-conflict-1");

		recordTransaction(paymentId, "CAPTURE", "100.00", isConflict());
	}

	private org.springframework.test.web.servlet.ResultMatcher isConflict() {
		return status().isConflict();
	}

	@Test
	void refundBeforeCaptureReturns409() throws Exception {
		long merchantId = createMerchant("refund-early@ledger.test");
		long paymentId = createPaymentViaApi(merchantId, "key-refund-early");

		recordTransaction(paymentId, "REFUND", "30.00", isConflict());
	}

	@Test
	void unknownMerchantReturns404() throws Exception {
		mockMvc.perform(post("/api/payments")
				.header("Merchant-Id", 424242)
				.contentType(MediaType.APPLICATION_JSON)
				.content(paymentBody(424242, "key-unknown-merchant")))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error").value("Not Found"));
	}

	@Test
	void unknownPaymentTransactionPostReturns404() throws Exception {
		recordTransaction(987654, "AUTHORIZATION", "10.00", status().isNotFound());
	}

	@Test
	void malformedAmountReturns400() throws Exception {
		long merchantId = createMerchant("malformed@ledger.test");
		long paymentId = createPaymentViaApi(merchantId, "key-malformed-1");

		mockMvc.perform(post("/api/payments/" + paymentId + "/transactions")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"type": "AUTHORIZATION", "amount": "abc"}
						"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").isNotEmpty());
	}

	@Test
	void listFiltersByMerchantIdAndStatus() throws Exception {
		long merchantId = createMerchant("filters@ledger.test");
		createPaymentViaApi(merchantId, "key-filter-1");
		createPaymentViaApi(merchantId, "key-filter-2");

		mockMvc.perform(get("/api/payments").param("merchantId", String.valueOf(merchantId)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(2)));

		mockMvc.perform(get("/api/payments").param("merchantId", String.valueOf(merchantId))
				.param("status", "REQUIRES_PAYMENT"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(2)));

		// Move one payment to AUTHORIZED, then the status filter must exclude it.
		String listJson = mockMvc.perform(get("/api/payments").param("merchantId", String.valueOf(merchantId)))
				.andReturn().getResponse().getContentAsString();
		long firstPaymentId = Long.parseLong(listJson.replaceAll(".*\"id\":(\\d+).*", "$1"));
		recordTransaction(firstPaymentId, "AUTHORIZATION", "100.00", isCreated());

		mockMvc.perform(get("/api/payments").param("merchantId", String.valueOf(merchantId))
				.param("status", "REQUIRES_PAYMENT"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(1)));
	}

	private long createPaymentViaApi(long merchantId, String key) throws Exception {
		String response = mockMvc.perform(post("/api/payments")
				.header("Merchant-Id", merchantId)
		.contentType(MediaType.APPLICATION_JSON)
				.content(paymentBody(merchantId, key)))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return Long.parseLong(response.replaceAll(".*\"id\":(\\d+).*", "$1"));
	}
}
