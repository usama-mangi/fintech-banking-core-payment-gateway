package com.fintech.ledger.PaymentGateway.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.fintech.ledger.PaymentGateway.domain.Transaction;
import com.fintech.ledger.PaymentGateway.domain.TransactionStatus;
import com.fintech.ledger.PaymentGateway.domain.TransactionType;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {

	List<Transaction> findByPaymentIdOrderByRecordedAtAsc(Long paymentId);

	List<Transaction> findByPaymentIdAndType(Long paymentId, TransactionType type);

	List<Transaction> findByPaymentIdAndStatus(Long paymentId, TransactionStatus status);
}
