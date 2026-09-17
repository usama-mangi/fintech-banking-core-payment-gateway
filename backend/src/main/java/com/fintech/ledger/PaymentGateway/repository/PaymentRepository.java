package com.fintech.ledger.PaymentGateway.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.fintech.ledger.PaymentGateway.domain.Payment;
import com.fintech.ledger.PaymentGateway.domain.PaymentStatus;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

	Optional<Payment> findByIdempotencyKey(String idempotencyKey);

	List<Payment> findByMerchantId(Long merchantId);

	List<Payment> findByMerchantIdAndStatus(Long merchantId, PaymentStatus status);

	List<Payment> findByStatus(PaymentStatus status);
}
