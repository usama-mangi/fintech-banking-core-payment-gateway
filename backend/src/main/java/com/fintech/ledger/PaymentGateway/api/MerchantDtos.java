package com.fintech.ledger.PaymentGateway.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request/response contracts for the merchant API. Records keep entities out
 * of the HTTP contract.
 */
public final class MerchantDtos {

	private MerchantDtos() {
	}

	public record CreateMerchantRequest(
			@NotBlank @Size(max = 255) String businessName,
			@NotBlank @Email @Size(max = 255) String email) {
	}

	public record MerchantResponse(
			Long id,
			String businessName,
			String email,
			String apiKey,
			String status,
			String createdAt) {
	}
}
