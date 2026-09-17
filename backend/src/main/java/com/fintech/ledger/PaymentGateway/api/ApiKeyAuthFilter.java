package com.fintech.ledger.PaymentGateway.api;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

import tools.jackson.databind.ObjectMapper;
import com.fintech.ledger.PaymentGateway.domain.Merchant;
import com.fintech.ledger.PaymentGateway.domain.MerchantStatus;
import com.fintech.ledger.PaymentGateway.repository.MerchantRepository;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Authenticates payment API calls with a merchant API key in {@code X-API-Key}.
 * Resolves the merchant server-side and exposes it via the
 * {@code authenticatedMerchantId} request attribute. Missing or unknown keys
 * get 401; a merchant that is not ACTIVE gets 403. Merchant and actuator
 * endpoints are outside this filter's URL pattern (MVP admin trust model).
 */
public class ApiKeyAuthFilter implements Filter {

	public static final String HEADER = "X-API-Key";
	public static final String MERCHANT_ATTRIBUTE = "authenticatedMerchantId";
	public static final String INTERNAL_ATTRIBUTE = "internalRequest";

	private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

	private final MerchantRepository merchantRepository;
	private final String internalApiKey;

	public ApiKeyAuthFilter(MerchantRepository merchantRepository, String internalApiKey) {
		this.merchantRepository = merchantRepository;
		this.internalApiKey = internalApiKey;
	}

	@Override
	public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
			throws IOException, ServletException {
		HttpServletRequest httpRequest = (HttpServletRequest) request;
		HttpServletResponse httpResponse = (HttpServletResponse) response;

		String apiKey = httpRequest.getHeader(HEADER);

		// The internal key (ops/portal/export) bypasses merchant binding;
		// merchant keys are scoped to their own data downstream.
		if (internalApiKey != null && !internalApiKey.isBlank() && internalApiKey.equals(apiKey)) {
			httpRequest.setAttribute(INTERNAL_ATTRIBUTE, true);
			chain.doFilter(request, response);
			return;
		}

		Merchant merchant = apiKey == null ? null : merchantRepository.findByApiKey(apiKey).orElse(null);

		if (merchant == null) {
			writeError(httpResponse, HttpStatus.UNAUTHORIZED, "missing or unknown API key");
			return;
		}

		// Fee revenue reporting is internal-only; merchant keys never pass.
		if (httpRequest.getRequestURI().equals("/api/fees")) {
			writeError(httpResponse, HttpStatus.FORBIDDEN, "fee reporting requires the internal key");
			return;
		}

		if (merchant.getStatus() != MerchantStatus.ACTIVE) {
			writeError(httpResponse, HttpStatus.FORBIDDEN, "merchant is " + merchant.getStatus().name().toLowerCase());
			return;
		}
		httpRequest.setAttribute(MERCHANT_ATTRIBUTE, merchant.getId());
		chain.doFilter(request, response);
	}

	private void writeError(HttpServletResponse response, HttpStatus status, String message) throws IOException {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("status", status.value());
		body.put("error", status.getReasonPhrase());
		body.put("message", message);
		body.put("timestamp", Instant.now().toString());
		response.setStatus(status.value());
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		response.setCharacterEncoding(StandardCharsets.UTF_8.name());
		response.getWriter().write(OBJECT_MAPPER.writeValueAsString(body));
	}
}
