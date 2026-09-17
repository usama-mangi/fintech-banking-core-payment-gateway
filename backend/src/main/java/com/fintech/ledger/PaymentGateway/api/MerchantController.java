package com.fintech.ledger.PaymentGateway.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

/**
 * HTTP boundary for merchants. Maps DTOs to service calls; no domain logic
 * lives here.
 */
@RestController
@RequestMapping("/api/merchants")
public class MerchantController {

	private final MerchantService merchantService;

	public MerchantController(MerchantService merchantService) {
		this.merchantService = merchantService;
	}

	@PostMapping
	public ResponseEntity<MerchantDtos.MerchantResponse> create(@Valid @RequestBody MerchantDtos.CreateMerchantRequest request) {
		MerchantDtos.MerchantResponse created = merchantService.create(request);
		return ResponseEntity.created(URI.create("/api/merchants/" + created.id())).body(created);
	}

	@GetMapping
	public List<MerchantDtos.MerchantResponse> list(@RequestParam(required = false) MerchantStatusParam status) {
		return merchantService.list(status == null ? null : status.merchantStatus());
	}

	/**
	 * Case-insensitive binding of the {@code status} query parameter to a
	 * {@link com.fintech.ledger.PaymentGateway.domain.MerchantStatus}.
	 */
	enum MerchantStatusParam {
		PENDING, ACTIVE, SUSPENDED, CLOSED;

		com.fintech.ledger.PaymentGateway.domain.MerchantStatus merchantStatus() {
			return com.fintech.ledger.PaymentGateway.domain.MerchantStatus.valueOf(name());
		}
	}

	@GetMapping("/{id}")
	public MerchantDtos.MerchantResponse get(@PathVariable Long id) {
		return merchantService.getById(id);
	}
}
