package com.fintech.ledger.PaymentGateway.api;

import java.net.URI;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

import com.fintech.ledger.PaymentGateway.domain.PaymentStatus;

/**
 * HTTP boundary for payments and their transaction ledger.
 */
@RestController
@RequestMapping("/api/payments")
public class PaymentController {

	private final PaymentService paymentService;

	public PaymentController(PaymentService paymentService) {
		this.paymentService = paymentService;
	}

	@PostMapping
	public ResponseEntity<PaymentDtos.PaymentSummary> create(
			@RequestHeader(name = "Merchant-Id", required = false) Long merchantId,
			@Valid @RequestBody PaymentDtos.CreatePaymentRequest request) {
		PaymentService.CreatedPayment result = paymentService.create(merchantId, request);
		if (result.created()) {
			return ResponseEntity.created(URI.create("/api/payments/" + result.payment().id())).body(result.payment());
		}
		return ResponseEntity.ok(result.payment());
	}

	@GetMapping
	public List<PaymentDtos.PaymentSummary> list(
			@RequestParam(name = "merchantId", required = false) Long merchantId,
			@RequestParam(name = "status", required = false) PaymentStatus status) {
		return paymentService.list(merchantId, status);
	}

	@GetMapping("/{id}")
	public PaymentDtos.PaymentDetail get(@PathVariable Long id) {
		return paymentService.getDetail(id);
	}

	@PostMapping("/{id}/transactions")
	public ResponseEntity<PaymentDtos.TransactionResponse> recordTransaction(
			@PathVariable Long id,
			@Valid @RequestBody PaymentDtos.RecordTransactionRequest request) {
		PaymentDtos.TransactionResponse transaction = paymentService.recordTransaction(id, request);
		return ResponseEntity.created(URI.create("/api/payments/" + id + "/transactions/" + transaction.id()))
				.body(transaction);
	}
}
