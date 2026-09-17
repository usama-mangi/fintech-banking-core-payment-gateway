package com.fintech.ledger.PaymentGateway.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.fintech.ledger.PaymentGateway.domain.Transaction;
import com.fintech.ledger.PaymentGateway.domain.TransactionStatus;
import com.fintech.ledger.PaymentGateway.domain.TransactionType;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {

	List<Transaction> findByPaymentIdOrderByRecordedAtAsc(Long paymentId);

	List<Transaction> findByPaymentIdAndType(Long paymentId, TransactionType type);

	List<Transaction> findByPaymentIdAndStatus(Long paymentId, TransactionStatus status);

	/**
	 * Fee revenue per merchant and currency. Fees are gateway revenue in the
	 * payment's currency; amounts stay exact decimals summed in SQL.
	 */
	@Query("""
			select t.payment.merchant.id,
			       t.payment.merchant.businessName,
			       t.payment.currency,
			       sum(t.amount),
			       count(t)
			from Transaction t
			where t.type = com.fintech.ledger.PaymentGateway.domain.TransactionType.FEE
			group by t.payment.merchant.id, t.payment.merchant.businessName, t.payment.currency
			having sum(t.amount) > 0
			order by sum(t.amount) desc
			""")
	List<Object[]> sumFeesByMerchant();
}
