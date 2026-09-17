package com.fintech.ledger.PaymentGateway.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * The merchant self-service dashboard: API-key sign-in, cookie session,
 * ownership-scoped content, sign-out.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@TestPropertySource(properties = { "internal.api.key=internal-dash-test-key" })
class MerchantDashboardTests {

	@Autowired
	private MockMvc mockMvc;

	private final ObjectMapper json = new ObjectMapper();

	private String registerMerchant(String name) throws Exception {
		String body = mockMvc.perform(post("/api/merchants")
				.contentType("application/json")
				.content("{\"businessName\": \"" + name + "\", \"email\": \"" + name.toLowerCase().replace(" ", "")
						+ Math.abs(java.util.UUID.randomUUID().toString().hashCode()) + "@dash.test\"}"))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return json.readTree(body).path("apiKey").asText();
	}

	private String createPayment(String key, String amount) throws Exception {
		String description = "dash-" + java.util.UUID.randomUUID();
		String idempotencyKey = "dash-" + java.util.UUID.randomUUID();
		String body = mockMvc.perform(post("/api/payments")
				.header("X-API-Key", key)
				.contentType("application/json")
				.content("{\"amount\": \"" + amount + "\", \"currency\": \"USD\", \"description\": \"" + description
						+ "\", \"idempotencyKey\": \"" + idempotencyKey + "\"}"))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return json.readTree(body).path("id").asText();
	}

	/** Drives a payment through AUTHORIZATION and CAPTURE so its amount
	 *  counts in the dashboard's captured total. */
	private void capturePayment(String key, String paymentId, String amount) throws Exception {
		mockMvc.perform(post("/api/payments/" + paymentId + "/transactions")
				.header("X-API-Key", key)
				.contentType("application/json")
				.content("{\"type\": \"AUTHORIZATION\", \"amount\": \"" + amount + "\"}"))
				.andExpect(status().isCreated());
		mockMvc.perform(post("/api/payments/" + paymentId + "/transactions")
				.header("X-API-Key", key)
				.contentType("application/json")
				.content("{\"type\": \"CAPTURE\", \"amount\": \"" + amount + "\"}"))
				.andExpect(status().isCreated());
	}

	@Test
	void dashboardWithoutKeyShowsLogin() throws Exception {
		mockMvc.perform(get("/dashboard"))
				.andExpect(status().isOk())
				.andExpect(view().name("dashboard-login"))
				.andExpect(content().string(org.hamcrest.Matchers.containsString("API key")));
	}

	@Test
	void dashboardRejectsWrongKey() throws Exception {
		mockMvc.perform(get("/dashboard").param("key", "sk_not-a-real-key"))
				.andExpect(status().isOk())
				.andExpect(view().name("dashboard-login"))
				.andExpect(content().string(org.hamcrest.Matchers.containsString("Invalid or missing")));
	}

	@Test
	void dashboardShowsOnlyOwnPaymentsAndTotals() throws Exception {
		String keyA = registerMerchant("Dash A Co");
		String keyB = registerMerchant("Dash B Co");
		createPayment(keyA, "100.00");
		capturePayment(keyA, createPayment(keyA, "50.00"), "50.00");
		String refundedId = createPayment(keyA, "30.00");
		capturePayment(keyA, refundedId, "30.00");
		mockMvc.perform(post("/api/payments/" + refundedId + "/transactions")
				.header("X-API-Key", keyA)
				.contentType("application/json")
				.content("{\"type\": \"REFUND\", \"amount\": \"30.00\"}"))
				.andExpect(status().isCreated());
		createPayment(keyB, "999.00");

		// Sign in via key param; the key moves to a cookie.
		MvcResult signin = mockMvc.perform(get("/dashboard").param("key", keyA))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/dashboard"))
				.andExpect(cookie().httpOnly("merchant_dashboard_key", true))
				.andReturn();

		String html = mockMvc.perform(get("/dashboard").cookie(signin.getResponse().getCookies()))
				.andExpect(status().isOk())
				.andExpect(view().name("dashboard"))
				.andReturn().getResponse().getContentAsString();

		org.assertj.core.api.Assertions.assertThat(html)
				.contains("Dash A Co")
				.contains("100.00")
				.contains("50.00")
				.contains("30.00")
				.contains("50.00</div>") // captured: only the captured payment
				.contains("30.00</div>") // refunded: only the refunded payment
				.contains("3</div>") // payment count
				.doesNotContain("999.00") // B's payment never leaks
				.doesNotContain(keyA); // the key itself is never rendered
	}

	@Test
	void dashboardSignOutClearsCookie() throws Exception {
		String keyA = registerMerchant("Dash Out Co");
		createPayment(keyA, "10.00");

		MvcResult signin = mockMvc.perform(get("/dashboard").param("key", keyA))
				.andReturn();
		jakarta.servlet.http.Cookie cookie = signin.getResponse().getCookie("merchant_dashboard_key");

		MvcResult logout = mockMvc.perform(post("/dashboard/logout").cookie(cookie))
				.andExpect(status().is3xxRedirection())
				.andExpect(cookie().maxAge("merchant_dashboard_key", 0))
				.andReturn();

		// After logout, the stale cookie no longer grants access.
		mockMvc.perform(get("/dashboard").cookie(logout.getResponse().getCookies()))
				.andExpect(view().name("dashboard-login"));
	}
}
