package com.fintech.ledger.PaymentGateway.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.fintech.ledger.PaymentGateway.domain.Merchant;
import com.fintech.ledger.PaymentGateway.domain.MerchantStatus;

public interface MerchantRepository extends JpaRepository<Merchant, Long> {

	Optional<Merchant> findByEmail(String email);

	Optional<Merchant> findByApiKey(String apiKey);

	List<Merchant> findByStatus(MerchantStatus status);

	Page<Merchant> findByStatus(MerchantStatus status, Pageable pageable);

	boolean existsByEmail(String email);
}
