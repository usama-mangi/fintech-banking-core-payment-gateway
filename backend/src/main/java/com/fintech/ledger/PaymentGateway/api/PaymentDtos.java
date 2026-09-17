package com.fintech.ledger.PaymentGateway.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request/response contracts for the payment API. Amounts travel as strings
 * with exactly two decimal places so no binary float touches the pipeline.
 */
public final class PaymentDtos {

	private PaymentDtos() {
	}

	public record CreatePaymentRequest(
			Long merchantId,
			@NotBlank @Pattern(regexp = "\\d+\\.\\d{2}", message = "amount must have exactly two decimal places, e.g. 100.00") String amount,
			@NotBlank @Size(min = 3, max = 3, message = "currency must be a 3-letter ISO 4217 code") String currency,
			@NotBlank @Size(max = 255) String description,
			@NotBlank @Size(max = 64) String idempotencyKey) {
	}

	public record RecordTransactionRequest(
			@NotBlank String type,
			@NotBlank @Pattern(regexp = "\\d+\\.\\d{2}", message = "amount must have exactly two decimal places, e.g. 100.00") String amount,
			String status) {
	}

	public record TransactionResponse(
			Long id,
			String type,
			String status,
			String amount,
			String recordedAt) {
	}

	public record PaymentSummary(
			Long id,
			Long merchantId,
			String amount,
			String currency,
			String status,
			String description,
			String idempotencyKey,
			String createdAt) {
	}

	public record PaymentDetail(
			Long id,
			Long merchantId,
			String amount,
			String currency,
			String status,
			String description,
			String idempotencyKey,
			String capturedAmountInUsd,
			String createdAt,
			java.util.List<TransactionResponse> transactions) {
	}
}
