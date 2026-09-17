package com.fintech.ledger.PaymentGateway.api;

import java.security.SecureRandom;
import java.util.Base64;

import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fintech.ledger.PaymentGateway.domain.Merchant;
import com.fintech.ledger.PaymentGateway.domain.MerchantStatus;
import com.fintech.ledger.PaymentGateway.repository.MerchantRepository;

/**
 * Merchant use cases: registration with API-key issuance, lookup, listing.
 */
@Service
public class MerchantService {

	private static final int API_KEY_BYTES = 32;

	private final MerchantRepository merchantRepository;
	private final SecureRandom secureRandom = new SecureRandom();

	public MerchantService(MerchantRepository merchantRepository) {
		this.merchantRepository = merchantRepository;
	}

	@Transactional
	public MerchantDtos.MerchantResponse create(MerchantDtos.CreateMerchantRequest request) {
		if (merchantRepository.existsByEmail(request.email())) {
			throw new ApiExceptions.DuplicateEmailException("a merchant with email " + request.email() + " already exists");
		}
		Merchant merchant = new Merchant(request.businessName().trim(), request.email().trim().toLowerCase(), generateApiKey());
		merchant = merchantRepository.save(merchant);
		return toResponse(merchant);
	}

	@Transactional(readOnly = true)
	public MerchantDtos.MerchantResponse getById(Long id) {
		Merchant merchant = merchantRepository.findById(id)
				.orElseThrow(() -> new ApiExceptions.NotFoundException("merchant " + id + " not found"));
		return toResponse(merchant);
	}

	@Transactional(readOnly = true)
	public PageResponse<MerchantDtos.MerchantResponse> list(MerchantStatus status, Pageable pageable) {
		var page = status == null
				? merchantRepository.findAll(pageable)
				: merchantRepository.findByStatus(status, pageable);
		return PageResponse.from(page, MerchantService::toResponse);
	}

	private String generateApiKey() {
		byte[] bytes = new byte[API_KEY_BYTES];
		secureRandom.nextBytes(bytes);
		return "sk_" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}

	private static MerchantDtos.MerchantResponse toResponse(Merchant merchant) {
		return new MerchantDtos.MerchantResponse(
				merchant.getId(),
				merchant.getBusinessName(),
				merchant.getEmail(),
				merchant.getApiKey(),
				merchant.getStatus().name(),
				merchant.getCreatedAt() == null ? null : merchant.getCreatedAt().toString());
	}
}
